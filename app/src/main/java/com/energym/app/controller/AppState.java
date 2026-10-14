package com.energym.app.controller;
import android.app.Application;
import android.net.*;
import androidx.lifecycle.AndroidViewModel;
import androidx.core.content.ContextCompat;
import com.energym.app.auth.*;
import com.energym.app.catalog.CatalogRepository;
import com.energym.app.data.EnerGymDatabase;
import com.energym.app.sync.*;
import com.energym.app.assistant.*;
import java.util.concurrent.*;
/** Propietario Android del ciclo de vida, no ViewModel de negocio: conserva los controladores MVC al rotar.
 * Un único worker serializa SQLite/cloud; todos los eventos que renderizan se entregan en main. */
public final class AppState extends AndroidViewModel {
    public AppController app;public AccountController accounts;public SyncController sync;public ProposalController assistant;
    public AccountController.Status accountStatus=AccountController.Status.CONFIGURATION_NEEDED;
    public SyncCoordinator.Status syncStatus=SyncCoordinator.Status.SIGNED_OUT;
    public AccountUser user;public String fatal="",syncError="";
    private final ExecutorService worker=Executors.newSingleThreadExecutor();private final Executor main;
    // Una consulta HTTP lenta no debe bloquear guardar perfiles/sesiones en el worker SQLite/cloud.
    private final ExecutorService chatWorker=Executors.newSingleThreadExecutor();
    private GoogleAccountGateway gateway;private Runnable observer=()->{};private boolean cleared;
    private ConnectivityManager connectivity;private ConnectivityManager.NetworkCallback networkCallback;
    public AppState(Application application){super(application);main=ContextCompat.getMainExecutor(application);
        worker.execute(()->{try{CatalogRepository catalog=CatalogRepository.load(application);main.execute(()->{if(!cleared)initialize(application,catalog);});}catch(Exception e){main.execute(()->{fatal="No se pudo leer el catálogo: "+e.getMessage();changed();});}});
    }
    private void initialize(Application context,CatalogRepository catalog){
        gateway=new GoogleAccountGateway(context,main);
        sync=new SyncController(context,gateway,new FirebaseCloudGateway(gateway,worker),worker,main,(status,error)->{syncStatus=status;syncError=error==null?"":error.getMessage();if(app!=null&&status==SyncCoordinator.Status.SYNCED)app.reload();changed();});
        AndroidAppStore store=new AndroidAppStore(sync);app=new AppController(store,catalog);
        assistant=new ProposalController(store,new LocalConversationStore(context,worker),new HttpChatClient(chatWorker,main,(context.getApplicationInfo().flags&android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE)!=0),catalog.getExercises(),catalog.getVersion(),()->app.profile,this::changed,r->{app.routines.accepted=r;app.reload();});
        app.explanations((exercise,profile)->{assistant.explain(exercise);app.navigate(AppController.Screen.ASSISTANT);});
        app.observe(()->{assistant.activate(app.owner());changed();});
        accounts=new AccountController(gateway,sync,main,(status,value,error)->{accountStatus=status;user=value;
            if(status==AccountController.Status.AUTHENTICATED||(status==AccountController.Status.ERROR&&value!=null))app.activate(value.getUid());
            else if(status==AccountController.Status.DEMO_LOCAL)app.activate(EnerGymDatabase.DEMO_OWNER_UID);
            else if(status!=AccountController.Status.ERROR)app.activate(null);
            if(error!=null)app.error("Cuenta: "+error.getMessage());changed();});
        accounts.start();monitorNetwork(context);changed();
    }
    public void observe(Runnable observer){this.observer=observer==null?()->{}:observer;changed();}
    private void changed(){observer.run();}
    public String accountText(){if(accountStatus==AccountController.Status.DEMO_LOCAL)return "Demo · solo en este dispositivo";if(user!=null)return "Google · "+(user.getDisplayName().isEmpty()?user.getEmail():user.getDisplayName());if(accountStatus==AccountController.Status.SIGNING_IN)return "Conectando con Google…";return "Cuenta sin conectar";}
    public String syncText(){if(app!=null&&EnerGymDatabase.DEMO_OWNER_UID.equals(app.owner()))return "Sin respaldo cloud · demo local";switch(syncStatus){case SYNCED:return "Respaldo confirmado";case SYNCING:return "Sincronizando · respaldo pendiente";case CONFLICT:return "Conflicto · cambios locales pendientes";case ERROR:return "Error de respaldo · reintenta con conexión";case IDLE:return "Respaldo pendiente";default:return "Conecta Google para respaldar";}}
    /** Reintento solo al recuperar conectividad, sin descargas por pulsación ni sondeo periódico. */
    private void monitorNetwork(Application context){connectivity=context.getSystemService(ConnectivityManager.class);networkCallback=new ConnectivityManager.NetworkCallback(){private boolean disconnected;
        @Override public void onLost(Network network){disconnected=true;}
        @Override public void onAvailable(Network network){if(disconnected){disconnected=false;main.execute(()->{if(!cleared)sync.requestSync();});}}
    };try{connectivity.registerDefaultNetworkCallback(networkCallback);}catch(RuntimeException e){networkCallback=null;}}
    @Override protected void onCleared(){cleared=true;observer=()->{};if(assistant!=null)assistant.disconnect();chatWorker.shutdown();if(app!=null)app.observe(null);if(networkCallback!=null)connectivity.unregisterNetworkCallback(networkCallback);if(accounts!=null)accounts.close();if(sync!=null)sync.close();worker.shutdown();}
}

