package com.energym.app.ui;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.energym.app.MainActivity;
import com.energym.app.R;
import org.junit.*;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;
/** Smoke opt-in con servidor localhost real desactivado. No se ejecuta sin parámetros privados de fixture. */
@RunWith(AndroidJUnit4.class)
public class BackendConnectionTest {
    @Test public void disabledProviderCanPairReportErrorContinueLocallyAndDisconnect()throws Exception{
        String code=InstrumentationRegistry.getArguments().getString("pairingCode");String url=InstrumentationRegistry.getArguments().getString("serverUrl");Assume.assumeTrue("Smoke requiere servidor local desactivado",code!=null&&url!=null);
        try(ActivityScenario<MainActivity> s=ActivityScenario.launch(MainActivity.class)){MainFlowTest.await();MainFlowTest.click(s,"Cuenta");boolean[] enter={false};s.onActivity(a->enter[0]=MainFlowTest.text(a.getWindow().getDecorView(),"Entrar en demo local")!=null);if(enter[0])MainFlowTest.click(s,"Entrar en demo local");MainFlowTest.await();s.onActivity(a->((com.google.android.material.bottomnavigation.BottomNavigationView)a.findViewById(R.id.navigation)).setSelectedItemId(R.id.tab_assistant));MainFlowTest.await();MainFlowTest.click(s,"Configurar conexión opcional");MainFlowTest.edit(s,"assistant_endpoint",url);MainFlowTest.edit(s,"assistant_pairing",code);MainFlowTest.click(s,"Conectar servidor");waitUntil(s,a->!a.state().assistant.busy);s.onActivity(a->{assertTrue(a.state().assistant.status,a.state().assistant.connected());assertEquals("",a.state().assistant.pairingCode);});MainFlowTest.edit(s,"assistant_message","¿Puedes ayudarme a preparar una rutina?");MainFlowTest.click(s,"Enviar al servidor opcional");s.recreate();waitUntil(s,a->!a.state().assistant.busy);s.onActivity(a->{assertTrue(a.state().assistant.status,a.state().assistant.status.contains("desactivado"));assertTrue(MainFlowTest.textContaining(a.getWindow().getDecorView(),"continuar con Enviar local"));});AssistantFlowTest.capture("server-disabled");MainFlowTest.edit(s,"assistant_message","3 dias en casa sin material 30min fuerza principiante");MainFlowTest.click(s,"Enviar local");s.onActivity(a->assertNotNull(a.state().assistant.preview));MainFlowTest.click(s,"Desconectar y revocar");s.onActivity(a->{assertFalse(a.state().assistant.connected());assertFalse(a.state().assistant.busy);});}
    }
    interface Condition{boolean test(MainActivity a);}private static void waitUntil(ActivityScenario<MainActivity> s,Condition test){boolean[] ready={false};for(int i=0;i<90&&!ready[0];i++){MainFlowTest.await();s.onActivity(a->ready[0]=test.test(a));}assertTrue("El servidor no terminó dentro del límite",ready[0]);}
}
