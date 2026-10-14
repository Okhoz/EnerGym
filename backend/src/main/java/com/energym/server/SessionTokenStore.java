package com.energym.server;
import java.time.*;
import java.security.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
/** Modelo de emparejamiento local; nunca equivale a una cuenta Google ni autoriza datos cloud. */
public final class SessionTokenStore {
 public record Token(String token,long expiresAtEpochSeconds){}
 private record Session(Instant expiry,ArrayDeque<Instant> requests){}
 private final Map<String,Session> sessions=new HashMap<>();
 private byte[] codeHash;
 private final Clock clock;private final Duration ttl;private final int limit;
 private final SecureRandom random=new SecureRandom();
 private final ArrayDeque<Instant> pairingAttempts=new ArrayDeque<>();
 public SessionTokenStore(String code,Clock clock,Duration ttl,int limit){
  if(code!=null&&!code.isBlank()&&code.length()<24)throw new IllegalArgumentException("Código debe tener al menos 24 caracteres");
  this.codeHash=code==null||code.isBlank()?null:hash(code);this.clock=Objects.requireNonNull(clock);
  this.ttl=Objects.requireNonNull(ttl);if(ttl.isNegative()||ttl.compareTo(Duration.ofHours(24))>0||limit<1)throw new IllegalArgumentException("Límites inválidos");this.limit=limit;
 }
 /** Diez intentos por minuto para todo el servidor; no confía en cabeceras de IP. */
 public synchronized boolean allowPairingAttempt(){return allow(pairingAttempts,10);}
 /** Consume atómicamente el código; solo guarda hashes de código y tokens. */
 public synchronized Token pair(String code){
  if(codeHash==null||code==null||!MessageDigest.isEqual(codeHash,hash(code)))return null;
  codeHash=null;byte[] bytes=new byte[32];random.nextBytes(bytes);String token=Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  Instant expiry=clock.instant().plus(ttl);sessions.put(key(token),new Session(expiry,new ArrayDeque<>()));
  return new Token(token,expiry.getEpochSecond());
 }
 public synchronized boolean authenticated(String token){
  sessions.entrySet().removeIf(e->!e.getValue().expiry.isAfter(clock.instant()));
  return token!=null&&token.matches("[A-Za-z0-9_-]{43}")&&sessions.containsKey(key(token));
 }
 public synchronized void revoke(String token){if(token!=null)sessions.remove(key(token));}
 public synchronized boolean allowChat(String token){return authenticated(token)&&allow(sessions.get(key(token)).requests,limit);}
 private boolean allow(ArrayDeque<Instant> queue,int max){
  Instant now=clock.instant();while(!queue.isEmpty()&&!queue.peekFirst().isAfter(now.minusSeconds(60)))queue.removeFirst();
  if(queue.size()>=max)return false;queue.addLast(now);return true;
 }
 private static String key(String token){return HexFormat.of().formatHex(hash(token));}
 private static byte[] hash(String s){try{return MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8));}catch(NoSuchAlgorithmException e){throw new IllegalStateException(e);}}
}
