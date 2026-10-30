package skillbridge;

import com.sun.net.httpserver.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.regex.*;

/** Drives App.handle() with real request/response objects, database and session cookies.
 * No TCP socket is required. Run the separate Python suite on a normal host for transport testing. */
public final class HandlerTest {
    static int checks;
    static void check(boolean ok,String label){checks++;if(!ok)throw new AssertionError(label);System.out.println("PASS "+label);}
    record Response(int code,String body,Headers headers) {}
    static class Exchange extends HttpExchange {
        final Headers request=new Headers(),response=new Headers();
        final String method;final URI uri;final InputStream input;final ByteArrayOutputStream output=new ByteArrayOutputStream();int code;
        Exchange(String method,String uri,String body){this.method=method;this.uri=URI.create(uri);input=new ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8));}
        public Headers getRequestHeaders(){return request;}public Headers getResponseHeaders(){return response;}
        public URI getRequestURI(){return uri;}public String getRequestMethod(){return method;}public HttpContext getHttpContext(){return null;}
        public void close(){}public InputStream getRequestBody(){return input;}public OutputStream getResponseBody(){return output;}
        public void sendResponseHeaders(int code,long size){this.code=code;}public InetSocketAddress getRemoteAddress(){return new InetSocketAddress("127.0.0.1",43210);}
        public int getResponseCode(){return code;}public InetSocketAddress getLocalAddress(){return new InetSocketAddress("127.0.0.1",8080);}
        public String getProtocol(){return "HTTP/1.1";}public Object getAttribute(String name){return null;}public void setAttribute(String name,Object value){}
        public void setStreams(InputStream in,OutputStream out){}public HttpPrincipal getPrincipal(){return null;}
    }
    static class Client {
        final App app;String cookie="";
        Client(App app){this.app=app;}
        Response request(String method,String path,Map<String,String> data)throws Exception{
            StringJoiner body=new StringJoiner("&");for(var entry:data.entrySet())body.add(App.enc(entry.getKey())+"="+App.enc(entry.getValue()));
            Exchange x=new Exchange(method,path,body.toString());if(!cookie.isEmpty())x.request.set("Cookie",cookie);if(method.equals("POST"))x.request.set("Content-Type","application/x-www-form-urlencoded");app.handle(x);
            String set=x.response.getFirst("Set-Cookie");if(set!=null)cookie=set.split(";",2)[0];
            return new Response(x.code,x.output.toString(StandardCharsets.UTF_8),x.response);
        }
        Response get(String path)throws Exception{return follow(request("GET",path,Map.of()));}
        Response follow(Response r)throws Exception{int i=0;while(r.code()==303){if(++i>8)throw new AssertionError("Redirect loop");r=request("GET",r.headers().getFirst("Location"),Map.of());}return r;}
        String csrf(String page)throws Exception{Response r=get(page);Matcher m=Pattern.compile("name='csrf' value='([^']+)'").matcher(r.body());if(!m.find())throw new AssertionError("CSRF token missing: "+page+" "+r.body());return m.group(1);}
        Response post(String path,Map<String,String> fields)throws Exception{return post(path,fields,"/");}
        Response post(String path,Map<String,String> fields,String page)throws Exception{Map<String,String> data=new LinkedHashMap<>(fields);data.put("csrf",csrf(page));return follow(request("POST",path,data));}
        void login(String email,String password)throws Exception{Response r=post("/login",Map.of("email",email,"password",password),"/login");check(r.code()==200&&r.body().contains("Sign out"),"Login "+email);}
    }
    static String extract(String regex,String text){Matcher m=Pattern.compile(regex).matcher(text);if(!m.find())throw new AssertionError("Missing pattern "+regex);return m.group(1);}
    static void snapshot(String name,Response r)throws Exception{if(System.getenv("SB_SNAPSHOTS")!=null){Path dir=Path.of(System.getenv("SB_SNAPSHOTS"));Files.createDirectories(dir);Files.writeString(dir.resolve(name+".html"),r.body().replace("href='/style.css'","href='style.css'"));}}
    public static void main(String[] args)throws Exception{
        Files.createDirectories(Path.of("build"));Path temp=Files.createTempDirectory(Path.of("build"),"handler-");
        String url="jdbc:h2:"+temp.toAbsolutePath().resolve("testdb")+";DB_CLOSE_ON_EXIT=FALSE";
        Database db=new Database(url);db.initialize(Path.of("src/main/resources/schema.sql"),true);App app=new App(db,Path.of("src/main/resources/static"));
        Client anon=new Client(app),student=new Client(app),admin=new Client(app),other=new Client(app);
        Response login=anon.get("/applications");check(login.code()==200&&login.body().contains("Welcome back"),"Protected page redirects to login");check(login.headers().getFirst("Content-Security-Policy").contains("script-src 'none'"),"CSP header");
        check(anon.request("POST","/login",Map.of("email","student@skillbridge.local","password","Student@12345")).code()==403,"CSRF required at login");
        check(anon.post("/login",Map.of("email","student@skillbridge.local","password","incorrect123"),"/login").code()==400,"Wrong password rejected");
        snapshot("login",anon.get("/login"));
        student.login("student@skillbridge.local","Student@12345");admin.login("admin@skillbridge.local","Admin@12345");other.login("rahul@skillbridge.local","Student@12345");
        for(String path:List.of("/","/opportunities","/opportunities?sort=match","/opportunities?state=ALL","/opportunities?q=backend","/saved","/profile","/applications","/notifications","/analytics","/analytics?n=5&k=2&p=0.2&trials=1000&threshold=70","/reports","/opportunity?id=1"))check(student.get(path).code()==200,"Student GET "+path);
        for(String path:List.of("/","/admin/students","/admin/skills","/admin/audit","/admin/opportunity","/admin/opportunity?id=1","/applications","/analytics","/reports","/profile"))check(admin.get(path).code()==200,"Admin GET "+path);
        snapshot("student-dashboard",student.get("/"));snapshot("opportunities",student.get("/opportunities"));snapshot("profile",student.get("/profile"));snapshot("probability-lab",admin.get("/analytics"));snapshot("admin-dashboard",admin.get("/"));
        check(student.get("/admin/students").code()==403,"Admin directory role gate");check(student.get("/missing").code()==404,"Unknown route 404");check(student.get("/opportunity?id=99999").code()==404,"Unknown record 404");check(student.get("/opportunity?id=bad").code()==400,"Invalid ID 400");check(student.get("/analytics?n=1&k=5").code()==400,"Invalid simulator k");check(student.get("/analytics?p=NaN").code()==400,"Non-finite probability blocked");
        check(student.post("/save",Map.of("opportunity","1")).code()==200&&student.get("/saved").body().contains("Backend Engineering Intern"),"Save opportunity");student.post("/save",Map.of("opportunity","1"));check(student.get("/saved").body().contains("Nothing saved yet"),"Unsave opportunity");
        check(student.request("POST","/apply",Map.of("csrf","invalid","opportunity","1","cover_letter","Hello")).code()==403,"Invalid CSRF rejected");
        Response applied=student.post("/apply",Map.of("opportunity","1","cover_letter","I enjoy Java and SQL. <script>not executable</script>"));check(applied.code()==200&&applied.body().contains("Application submitted successfully"),"Apply with cover note");check(applied.body().contains("&lt;script&gt;")&&!applied.body().contains("<script>"),"Stored content escaped");
        String aid=extract("APPLICATION #(\\d+)",applied.body());String page="/application?id="+aid;
        check(other.get(page).code()==404,"Application read is owner-scoped");check(student.post("/apply",Map.of("opportunity","1","cover_letter","Duplicate")).code()==400,"Duplicate application blocked");
        check(other.post("/status",Map.of("application",aid,"version","0","status","WITHDRAWN")).code()==400,"Cross-student mutation blocked");
        check(student.post("/admin/status",Map.of("application",aid,"version","0","status","SHORTLISTED")).code()==403,"Admin endpoint role gate");
        check(admin.post("/admin/status",Map.of("application",aid,"version","0","status","OFFERED")).code()==400,"Cannot skip interview pipeline");
        check(admin.post("/admin/status",Map.of("application",aid,"version","0","status","SHORTLISTED","note","Technical review")).code()==200,"Admin shortlists");
        check(admin.post("/admin/status",Map.of("application",aid,"version","0","status","REJECTED")).code()==400,"Stale status update blocked");
        check(admin.post("/admin/status",Map.of("application",aid,"version","1","status","INTERVIEW")).code()==400,"Interview requires date and venue");
        String time=LocalDateTime.now().plusDays(3).withSecond(0).withNano(0).toString();
        check(admin.post("/admin/status",Map.of("application",aid,"version","1","status","INTERVIEW","interview_at",time,"location","Placement room 201","note","Technical round")).code()==200,"Interview scheduled");check(student.get(page).body().contains("Placement room 201"),"Student sees interview");snapshot("interview",student.get(page));
        check(admin.post("/admin/status",Map.of("application",aid,"version","2","status","OFFERED","note","Offer issued")).code()==200,"Offer issued");
        check(student.post("/status",Map.of("application",aid,"version","3","status","ACCEPTED","note","Thank you, I accept.")).code()==200,"Student accepts offer");check(admin.get(page).body().contains("Thank you, I accept."),"Timeline visible to office");
        snapshot("application",student.get(page));snapshot("application-desk",admin.get("/applications"));
        check(student.get("/notifications").body().contains("now accepted"),"Notification delivered");check(student.post("/notifications/read",Map.of()).code()==200,"Mark notifications read");
        Response csv=student.get("/reports/applications.csv");check(csv.code()==200&&csv.body().contains("ACCEPTED")&&csv.headers().getFirst("Content-Disposition").contains("attachment;"),"Student CSV download");check(!other.get("/reports/applications.csv").body().contains("Ananya Rao"),"CSV owner restriction");check(admin.get("/reports/applications.csv").body().contains("Ananya Rao"),"Admin campus CSV");check(admin.get("/admin/audit").body().contains("application submitted"),"Audit recorded");
        Client fresh=new Client(app);Response registered=fresh.post("/register",Map.of("name","Test Student","email","test.student@example.edu","password","Teststudent123","department","Computer Science","year","2027"),"/register");check(registered.code()==200&&registered.body().contains("Complete your profile"),"Self-registration");Client staleSession=new Client(app);staleSession.login("test.student@example.edu","Teststudent123");
        Map<String,String> profile=new LinkedHashMap<>(Map.of("name","=Test Student","department","Computer Science","cgpa","8.5","year","2027","bio","I build small Java systems.","portfolio","https://example.com/portfolio","skill_1","3","skill_2","2"));
        check(fresh.post("/profile",profile).code()==200,"Profile and skills updated");profile.put("cgpa","11");check(fresh.post("/profile",profile).code()==400,"CGPA validation");profile.put("cgpa","8.5");profile.put("portfolio","javascript:alert(1)");check(fresh.post("/profile",profile).code()==400,"Unsafe portfolio rejected");profile.put("portfolio","https://example.com/portfolio");
        Response freshApplication=fresh.post("/apply",Map.of("opportunity","2","cover_letter","I enjoy analysis."));check(freshApplication.code()==200,"Fresh student applies");String freshId=extract("APPLICATION #(\\d+)",freshApplication.body());check(fresh.get("/reports/applications.csv").body().contains("'=Test Student"),"CSV formula injection guarded");
        check(fresh.post("/status",Map.of("application",freshId,"version","0","status","WITHDRAWN","note","Plans changed")).code()==200,"Student withdrawal");check(fresh.post("/apply",Map.of("opportunity","2","cover_letter","Again")).code()==400,"Cannot bypass duplicate rule after withdrawal");
        check(admin.post("/admin/skill",Map.of("name","Database Design")).code()==200,"Office adds skill");check(admin.post("/admin/skill",Map.of("name","database design")).code()==400,"Skill duplicate case folding");
        Map<String,String> role=new LinkedHashMap<>(Map.of("title","Quality Engineer Intern","company","Oak Research","kind","INTERNSHIP","location","Remote","compensation","16000","cgpa","7","year","2027","deadline",LocalDate.now().plusDays(20).toString(),"description","Write deterministic tests for Java services and reliable SQL datasets.","status","OPEN"));role.put("skill_1","2");role.put("skill_2","2");
        Map<String,String> invalidRole=new LinkedHashMap<>(role);invalidRole.put("skill_1","0");invalidRole.put("skill_2","0");check(admin.post("/admin/opportunity",invalidRole).code()==400,"Required skill validation and creation rollback");
        Response created=admin.post("/admin/opportunity",role);check(created.code()==200&&created.body().contains("Quality Engineer Intern"),"Create opportunity");String oid=extract("/admin/opportunity\\?id=(\\d+)",created.body());role.put("id",oid);role.put("status","CLOSED");check(admin.post("/admin/opportunity",role).code()==200,"Close opportunity");check(student.post("/apply",Map.of("opportunity",oid,"cover_letter","Interested")).code()==400,"Closed role submission blocked");
        check(fresh.post("/password",Map.of("current_password","wrong","new_password","Updatedstudent123")).code()==400,"Current password required");check(fresh.post("/password",Map.of("current_password","Teststudent123","new_password","Updatedstudent123")).code()==200,"Password changed");check(staleSession.get("/").body().contains("Welcome back"),"Password change revokes other sessions");fresh.post("/logout",Map.of());check(fresh.get("/").body().contains("Welcome back"),"Logout revokes session");fresh.login("test.student@example.edu","Updatedstudent123");
        // New database repository and App instance simulate restart; H2 disk connections have closed.
        Database reopened=new Database(url);reopened.initialize(Path.of("src/main/resources/schema.sql"),true);Client again=new Client(new App(reopened,Path.of("src/main/resources/static")));again.login("student@skillbridge.local","Student@12345");String persisted=again.get(page).body();check(persisted.contains("Thank you, I accept.")&&persisted.contains("status-ACCEPTED"),"Disk persistence after application recreation");check(again.get("/opportunities?state=CLOSED").body().contains("Quality Engineer Intern"),"Opportunity edits persist");
        check(again.request("PUT","/",Map.of()).code()==405,"Unsupported HTTP method rejected");
        System.out.println("ALL "+checks+" HANDLER CHECKS PASSED");
    }
}
