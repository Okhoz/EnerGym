package com.energym.app.assistant;
import org.junit.Test;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import static org.junit.Assert.*;
/** Carrera HTTP real en loopback, sin proveedor: el fixture retiene DELETE mientras el hilo de UI consulta el cliente. */
public class HttpChatClientTest {
    @Test public void stalePairingRevocationNeverBlocksConnectedOrDisconnect()throws Exception{
        ExecutorService worker=Executors.newSingleThreadExecutor(),ui=Executors.newSingleThreadExecutor(),server=Executors.newSingleThreadExecutor();
        CountDownLatch pairingEntered=new CountDownLatch(1),releasePairing=new CountDownLatch(1),deleteEntered=new CountDownLatch(1),releaseDelete=new CountDownLatch(1);AtomicInteger callbacks=new AtomicInteger();
        try(ServerSocket socket=new ServerSocket(0,4,InetAddress.getByName("127.0.0.1"))){
            Future<?> serving=server.submit(()->{try{try(Socket post=socket.accept()){readRequest(post,"POST");pairingEntered.countDown();assertTrue(releasePairing.await(5,TimeUnit.SECONDS));reply(post,201,"{\"token\":\""+"t".repeat(43)+"\",\"expiresAtEpochSeconds\":"+(System.currentTimeMillis()/1000+3600)+"}");}try(Socket delete=socket.accept()){readRequest(delete,"DELETE");deleteEntered.countDown();assertTrue(releaseDelete.await(5,TimeUnit.SECONDS));reply(delete,200,"{\"revoked\":true}");}}catch(Exception e){throw new RuntimeException(e);}});
            HttpChatClient client=new HttpChatClient(worker,Runnable::run,true);client.connect("http://127.0.0.1:"+socket.getLocalPort(),"p".repeat(32),new ChatClient.Callback(){public void success(String json){callbacks.incrementAndGet();}public void failure(String code){callbacks.incrementAndGet();}});
            assertTrue("POST no llegó al fixture",pairingEntered.await(5,TimeUnit.SECONDS));client.disconnect();releasePairing.countDown();assertTrue("La credencial tardía no se revocó",deleteEntered.await(5,TimeUnit.SECONDS));
            Future<Boolean> responsive=ui.submit(()->{boolean connected=client.connected();client.disconnect();return connected;});
            try{assertFalse(responsive.get(750,TimeUnit.MILLISECONDS));}catch(TimeoutException e){fail("connected()/disconnect() bloqueados por DELETE de una credencial tardía");}finally{releaseDelete.countDown();}
            serving.get(3,TimeUnit.SECONDS);worker.submit(()->{}).get(3,TimeUnit.SECONDS);assertEquals("Una conexión obsoleta no debe emitir callback",0,callbacks.get());assertFalse(client.connected());
        }finally{releasePairing.countDown();releaseDelete.countDown();worker.shutdownNow();ui.shutdownNow();server.shutdownNow();}
    }
    private static void readRequest(Socket socket,String expected)throws Exception{socket.setSoTimeout(5000);BufferedReader in=new BufferedReader(new InputStreamReader(socket.getInputStream(),StandardCharsets.UTF_8));String first=in.readLine();assertNotNull(first);assertTrue(first.startsWith(expected+" /api/session "));int length=0;String line;while((line=in.readLine())!=null&&!line.isEmpty())if(line.toLowerCase(java.util.Locale.ROOT).startsWith("content-length:"))length=Integer.parseInt(line.substring(line.indexOf(':')+1).trim());for(int i=0;i<length;i++)assertTrue(in.read()!=-1);}
    private static void reply(Socket socket,int status,String json)throws IOException{byte[] bytes=json.getBytes(StandardCharsets.UTF_8);String header="HTTP/1.1 "+status+" OK\r\nContent-Type: application/json\r\nContent-Length: "+bytes.length+"\r\nConnection: close\r\n\r\n";OutputStream out=socket.getOutputStream();out.write(header.getBytes(StandardCharsets.UTF_8));out.write(bytes);out.flush();}
}
