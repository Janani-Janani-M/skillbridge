package skillbridge;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.*;
import java.util.*;
import java.util.concurrent.*;

public final class Auth {
    private static final SecureRandom RANDOM=new SecureRandom();
    public record Session(long userId,String csrf,long expires) {}
    public record Ticket(String token, Session session) {}
    private final Map<String,Session> sessions=new ConcurrentHashMap<>();
    private final Map<String,Attempt> attempts=new ConcurrentHashMap<>();
    private record Attempt(int count,long start) {}
    public static String token(){byte[] b=new byte[32];RANDOM.nextBytes(b);return Base64.getUrlEncoder().withoutPadding().encodeToString(b);}
    public Ticket create(long userId){long now=System.currentTimeMillis();sessions.entrySet().removeIf(e->e.getValue().expires()<now);String token=token();Session s=new Session(userId,token(),now+8*3600_000L);sessions.put(token,s);return new Ticket(token,s);}
    public Session get(String token){if(token==null)return null;Session s=sessions.get(token);if(s!=null&&s.expires()<System.currentTimeMillis()){sessions.remove(token);return null;}return s;}
    public void revokeUser(long userId){sessions.entrySet().removeIf(e->e.getValue().userId()==userId);}
    public void remove(String token){if(token!=null)sessions.remove(token);}
    public boolean limited(String address){long now=System.currentTimeMillis();attempts.entrySet().removeIf(e->now-e.getValue().start()>900_000);Attempt a=attempts.get(address);return a!=null&&a.count()>=10;}
    public void failed(String address){attempts.compute(address,(k,v)->v==null?new Attempt(1,System.currentTimeMillis()):new Attempt(v.count()+1,v.start()));}
    public void success(String address){attempts.remove(address);}
    public static String hash(String password)throws GeneralSecurityException{byte[] salt=new byte[16];RANDOM.nextBytes(salt);return "120000:"+Base64.getEncoder().encodeToString(salt)+":"+Base64.getEncoder().encodeToString(derive(password,salt,120000));}
    public static boolean verify(String password,String hash){try{String[] s=hash.split(":");return MessageDigest.isEqual(Base64.getDecoder().decode(s[2]),derive(password,Base64.getDecoder().decode(s[1]),Integer.parseInt(s[0])));}catch(Exception e){return false;}}
    private static byte[] derive(String password,byte[] salt,int rounds)throws GeneralSecurityException{PBEKeySpec spec=new PBEKeySpec(password.toCharArray(),salt,rounds,256);try{return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();}finally{spec.clearPassword();}}
    public static void passwordRule(String p){if(p.length()<10||p.length()>128||!p.chars().anyMatch(Character::isLetter)||!p.chars().anyMatch(Character::isDigit))throw new IllegalArgumentException("Use 10–128 characters, including a letter and a number.");}
}
