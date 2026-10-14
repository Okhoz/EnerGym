package com.energym.app.visual;
/** CONTROLADOR MVC: comandos y tiempo no dependen de Activity ni de JNI. */
public final class ViewerController {
    public interface View { void render(ViewerState state); }
    private final ResourceManifest resource;
    private final View view;
    private final ViewerCommandValidator validator = new ViewerCommandValidator();
    private boolean ready,active,playing=true,closed;
    private float playhead,speed=1,yaw=90,pitch=25,zoom=1;
    private String error="";
    public ViewerController(ResourceManifest resource, View view) { this.resource=resource;this.view=view; }
    /** La vista solo remite órdenes; este método decide si pueden alterar la escena actual. */
    public boolean command(ViewerCommand c) {
        if (!ready || closed || !validator.validate(c,resource)) return false;
        switch (c.action) {
            case PLAY: playing=true; if(playhead>=resource.duration)playhead=0; break;
            case PAUSE: playing=false; break;
            case SPEED: speed=c.amount; break;
            case MARKER: playing=false;playhead=resource.markers.get(c.target);break;
            case ZOOM: zoom=clamp(zoom*c.amount,.55f,2.8f);break;
            case ROTATE: yaw=(yaw+c.amount)%360;pitch=clamp(pitch+c.secondary,-70,75);break;
            case VIEW:
                zoom=1;pitch=25;
                yaw=c.target.equals("front")?0:c.target.equals("rear")?180:90;break;
            default: return false;
        }
        changed();return true;
    }
    public void ready() { if(!closed){ready=true;error="";changed();} }
    public void failure(String message) { if(!closed){ready=false;active=false;error=message;changed();} }
    public void close() { closed=true;active=false;ready=false;changed(); }
    public void active(boolean value) { if(!closed)active=value; }
    /** Solo avanza estando visible; el delta se limita para no saltar al regresar del fondo. */
    public void advance(float delta) {
        if(ready && active && playing && Float.isFinite(delta) && delta>0) playhead=(playhead+Math.min(delta,1f)*speed)%resource.duration;
    }
    /** Restauración saneada: nunca restaura listo/activo/cerrado de una instancia nativa antigua. */
    public void restore(ViewerState s) {
        if(s==null || closed || resource==null)return;
        playhead=Float.isFinite(s.playhead)?clamp(s.playhead,0,resource.duration):0;
        playing=s.playing; speed=resource.speeds.contains(s.speed)?s.speed:1;
        yaw=Float.isFinite(s.yaw)?s.yaw%360:90; pitch=Float.isFinite(s.pitch)?clamp(s.pitch,-70,75):25;
        zoom=Float.isFinite(s.zoom)?clamp(s.zoom,.55f,2.8f):1;
    }
    public ViewerState state() { return new ViewerState(ready,active,playing,closed,playhead,speed,yaw,pitch,zoom,error); }
    private void changed() { view.render(state()); }
    private static float clamp(float v,float min,float max) { return Math.max(min,Math.min(max,v)); }
}
