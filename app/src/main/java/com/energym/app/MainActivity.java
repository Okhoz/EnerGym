package com.energym.app;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import androidx.activity.EdgeToEdge;
import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.fragment.app.Fragment;
import com.energym.app.controller.*;
import com.energym.app.ui.*;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
/** VISTA raíz MVC: aplica insets, muestra fragmentos y remite navegación. Nunca ejecuta SQL ni crea planes. */
public final class MainActivity extends AppCompatActivity {
    private AppState state;private boolean rendering;
    public AppState state(){return state;}
    @Override protected void onCreate(Bundle saved){super.onCreate(saved);EdgeToEdge.enable(this);setContentView(R.layout.activity_main);
        // El diseño es oscuro también con el sistema en modo claro: iconos blancos en ambas barras.
        androidx.core.view.WindowInsetsControllerCompat barsController=new androidx.core.view.WindowInsetsControllerCompat(getWindow(),getWindow().getDecorView());
        barsController.setAppearanceLightStatusBars(false);barsController.setAppearanceLightNavigationBars(false);
        // La raíz aplica todos los márgenes: consumir evita que Material vuelva a añadir IME a la navegación.
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main),(view,insets)->{Insets bars=insets.getInsets(WindowInsetsCompat.Type.systemBars()|WindowInsetsCompat.Type.ime());view.setPadding(bars.left,bars.top,bars.right,bars.bottom);return WindowInsetsCompat.CONSUMED;});
        state=new ViewModelProvider(this).get(AppState.class);
        findViewById(R.id.account).setOnClickListener(v->{if(state.app!=null)state.app.navigate(AppController.Screen.ACCOUNT);});
        findViewById(R.id.back).setOnClickListener(v->{if(state.app!=null)state.app.back();});
        BottomNavigationView nav=findViewById(R.id.navigation);nav.setOnItemSelectedListener(item->{if(rendering||state.app==null)return true;AppController.Screen screen=AppController.Screen.HOME;int id=item.getItemId();if(id==R.id.tab_training)screen=AppController.Screen.TRAINING;else if(id==R.id.tab_assistant)screen=AppController.Screen.ASSISTANT;else if(id==R.id.tab_history)screen=AppController.Screen.HISTORY;state.app.navigate(screen);if(screen==AppController.Screen.HISTORY)state.app.reload();return true;});
        getOnBackPressedDispatcher().addCallback(this,new OnBackPressedCallback(true){public void handleOnBackPressed(){if(state.app!=null&&state.app.screen!=state.app.tab)state.app.back();else{setEnabled(false);getOnBackPressedDispatcher().onBackPressed();setEnabled(true);}}});
        state.observe(this::render);
    }
    private void render(){if(state==null||isFinishing()||getSupportFragmentManager().isStateSaved())return;
        ((TextView)findViewById(R.id.status)).setText(state.app==null?(state.fatal.isEmpty()?"Preparando tu espacio…":state.fatal):state.accountText()+" · "+state.syncText());
        if(state.app==null)return;rendering=true;try{AppController app=state.app;BottomNavigationView nav=findViewById(R.id.navigation);int tab=R.id.tab_home;if(app.tab==AppController.Screen.TRAINING)tab=R.id.tab_training;else if(app.tab==AppController.Screen.ASSISTANT)tab=R.id.tab_assistant;else if(app.tab==AppController.Screen.HISTORY)tab=R.id.tab_history;nav.setSelectedItemId(tab);findViewById(R.id.back).setVisibility(app.screen==app.tab?View.GONE:View.VISIBLE);
            Fragment current=getSupportFragmentManager().findFragmentById(R.id.content);String tag=app.screen.name();if(current==null||!tag.equals(current.getTag())){getSupportFragmentManager().beginTransaction().replace(R.id.content,fragment(app.screen),tag).commitNow();}else if(current instanceof ScreenFragment)((ScreenFragment)current).render();
            if(!app.error.isEmpty()){String error=app.error;app.dismissError();new MaterialAlertDialogBuilder(this).setTitle("Revisa esta acción").setMessage(error).setPositiveButton("Entendido",null).show();}
        }finally{rendering=false;}
    }
    private Fragment fragment(AppController.Screen screen){switch(screen){case TRAINING:return new TrainingFragment();case ASSISTANT:return new AssistantFragment();case HISTORY:return new HistoryFragment();case PROFILE:return new ProfileFragment();case ROUTINE:return new RoutineFragment();case SESSION:return new SessionFragment();case EXERCISE:return new ExerciseFragment();case ACCOUNT:return new AccountFragment();default:return new HomeFragment();}}
    @Override protected void onResume(){super.onResume();render();}
    @Override protected void onDestroy(){state.observe(null);super.onDestroy();}
}
