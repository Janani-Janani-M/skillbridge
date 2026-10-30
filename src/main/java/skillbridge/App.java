package skillbridge;

import com.sun.net.httpserver.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.sql.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static skillbridge.Database.*;

/** Small server-rendered MVC application: no CDN, frontend build chain or remote service. */
public final class App {
    final Database db;
    final Workflow flow;
    final Auth auth=new Auth();
    final Views views;
    final Path assets;
    public App(Database db,Path assets){this.db=db;this.flow=new Workflow(db);this.assets=assets;this.views=new Views(db,flow);}
    public static void main(String[] args)throws Exception{
        String dbUrl=System.getenv().getOrDefault("SB_DB_URL","jdbc:h2:./data/skillbridge;DB_CLOSE_ON_EXIT=FALSE;LOCK_TIMEOUT=10000");
        Files.createDirectories(Path.of("data"));
        Database db=new Database(dbUrl);db.initialize(Path.of("src/main/resources/schema.sql"),true);
        int port=Integer.parseInt(System.getenv().getOrDefault("PORT","8080"));
        String host=System.getenv().getOrDefault("SB_HOST","127.0.0.1");
        HttpServer server=HttpServer.create(new InetSocketAddress(host,port),64);
        ThreadPoolExecutor executor=new ThreadPoolExecutor(4,16,60,TimeUnit.SECONDS,new ArrayBlockingQueue<>(128),new ThreadPoolExecutor.CallerRunsPolicy());
        server.setExecutor(executor);server.createContext("/",new App(db,Path.of("src/main/resources/static"))::handle);
        Runtime.getRuntime().addShutdownHook(new Thread(()->{server.stop(1);executor.shutdown();}));
        server.start();System.out.println("SkillBridge is ready at http://"+host+":"+port+" (Ctrl+C to stop)");
    }
    void handle(HttpExchange x)throws IOException{
        String path=x.getRequestURI().getPath();
        x.getResponseHeaders().set("X-Content-Type-Options","nosniff");
        x.getResponseHeaders().set("X-Frame-Options","DENY");
        x.getResponseHeaders().set("Referrer-Policy","same-origin");
        x.getResponseHeaders().set("Content-Security-Policy","default-src 'self'; style-src 'self'; script-src 'none'; base-uri 'none'; frame-ancestors 'none'; form-action 'self'");
        x.getResponseHeaders().set("Cache-Control","no-store");
        try{
            if(path.equals("/style.css")){if(!x.getRequestMethod().equals("GET"))throw new HttpError(405,"Method not allowed.");send(x,200,"text/css; charset=utf-8",Files.readString(assets.resolve("style.css")));return;}
            if(path.equals("/favicon.ico")){x.sendResponseHeaders(204,-1);return;}
            if(!List.of("GET","POST").contains(x.getRequestMethod()))throw new HttpError(405,"Method not allowed.");
            String cookie=cookie(x,"SBSESSION");Auth.Session s=auth.get(cookie);
            if(s==null){Auth.Ticket t=auth.create(0);s=t.session();cookie=t.token();setCookie(x,cookie);}
            Map<String,String> p=params(x.getRequestURI().getRawQuery());
            if(x.getRequestMethod().equals("POST")){
                String type=x.getRequestHeaders().getFirst("Content-Type");
                if(type==null||!type.toLowerCase(Locale.ROOT).startsWith("application/x-www-form-urlencoded"))throw new HttpError(415,"Use a standard form submission.");
                byte[] body=x.getRequestBody().readNBytes(65537);if(body.length>65536)throw new HttpError(413,"This form is too large.");
                p.clear();p.putAll(params(new String(body,StandardCharsets.UTF_8)));
                if(!Objects.equals(s.csrf(),p.get("csrf")))throw new HttpError(403,"Your form expired. Reload the page and try again.");
            }
            if(path.equals("/login")||path.equals("/register")){
                if(x.getRequestMethod().equals("GET")){if(s.userId()!=0){redirect(x,"/");return;}send(x,200,"text/html; charset=utf-8",views.auth(path.equals("/register"),s.csrf(),p.getOrDefault("message","")));return;}
                if(path.equals("/register")){
                    String name=text(p,"name",2,100),email=email(p),password=text(p,"password",10,128);Auth.passwordRule(password);
                    long id=db.tx(c->{if(one(c,"SELECT id FROM users WHERE email=?",email)!=null)throw new IllegalArgumentException("An account with this email already exists.");return insert(c,"INSERT INTO users(full_name,email,password_hash,role,department,cgpa,graduation_year) VALUES(?,?,?,'STUDENT',?,0,?)",name,email,Auth.hash(password),text(p,"department",2,100),integer(p,"year",2000,2100));});
                    auth.remove(cookie);Auth.Ticket t=auth.create(id);setCookie(x,t.token());redirect(x,"/profile?message="+enc("Account created. Complete your profile and skills."));return;
                }
                String address=x.getRemoteAddress().getAddress().getHostAddress();if(auth.limited(address))throw new HttpError(429,"Too many sign-in attempts. Try again in 15 minutes.");
                String email=p.getOrDefault("email","").trim().toLowerCase(Locale.ROOT),password=p.getOrDefault("password","");
                if(email.length()>180||password.length()>128)throw new IllegalArgumentException("Invalid email or password.");
                var user=db.one("SELECT * FROM users WHERE email=?",email);
                if(user==null||!Auth.verify(password,user.get("password_hash").toString())){auth.failed(address);throw new IllegalArgumentException("Invalid email or password.");}
                auth.success(address);auth.remove(cookie);Auth.Ticket t=auth.create(number(user,"id"));setCookie(x,t.token());redirect(x,"/");return;
            }
            var user=s.userId()==0?null:db.one("SELECT * FROM users WHERE id=?",s.userId());
            if(user==null){redirect(x,"/login");return;}
            boolean admin="ADMIN".equals(user.get("role"));long uid=number(user,"id");
            if(x.getRequestMethod().equals("POST")){
                if(path.equals("/logout")){auth.remove(cookie);setCookie(x,"");redirect(x,"/login");return;}
                if(path.startsWith("/admin/"))requireAdmin(admin);
                switch(path){
                    case "/profile" -> {requireStudent(admin);saveProfile(uid,p);redirect(x,"/profile?message="+enc("Profile updated."));}
                    case "/password" -> {
                        if(!Auth.verify(p.getOrDefault("current_password",""),user.get("password_hash").toString()))throw new IllegalArgumentException("Current password is incorrect.");
                        String password=text(p,"new_password",10,128);Auth.passwordRule(password);
                        db.tx(c->{update(c,"UPDATE users SET password_hash=? WHERE id=?",Auth.hash(password),uid);return null;});
                        auth.revokeUser(uid);Auth.Ticket t=auth.create(uid);setCookie(x,t.token());redirect(x,"/profile?message="+enc("Password changed."));
                    }
                    case "/apply" -> {requireStudent(admin);long id=flow.apply(uid,id(p,"opportunity"),text(p,"cover_letter",1,3000));redirect(x,"/application?id="+id+"&message="+enc("Application submitted successfully."));}
                    case "/save" -> {requireStudent(admin);long opportunity=id(p,"opportunity");db.tx(c->{if(one(c,"SELECT id FROM opportunities WHERE id=?",opportunity)==null)throw new HttpError(404,"Opportunity not found.");if(one(c,"SELECT user_id FROM saved_opportunities WHERE user_id=? AND opportunity_id=?",uid,opportunity)!=null)update(c,"DELETE FROM saved_opportunities WHERE user_id=? AND opportunity_id=?",uid,opportunity);else update(c,"INSERT INTO saved_opportunities(user_id,opportunity_id) VALUES(?,?)",uid,opportunity);return null;});redirect(x,"/opportunity?id="+opportunity);}
                    case "/status", "/admin/status" -> {
                        long id=id(p,"application");LocalDateTime at=null;String date=p.getOrDefault("interview_at","");if(!date.isBlank())at=LocalDateTime.parse(date);
                        flow.transition(uid,id,text(p,"status",1,20),integer(p,"version",0,Integer.MAX_VALUE),p.getOrDefault("note",""),at,p.getOrDefault("location",""));redirect(x,"/application?id="+id+"&message="+enc("Status updated."));
                    }
                    case "/notifications/read" -> {db.tx(c->{update(c,"UPDATE notifications SET is_read=TRUE WHERE user_id=?",uid);return null;});redirect(x,"/notifications?message="+enc("Notifications marked as read."));}
                    case "/admin/opportunity" -> {long oid=saveOpportunity(uid,p);redirect(x,"/opportunity?id="+oid+"&message="+enc("Opportunity saved."));}
                    case "/admin/skill" -> {String name=text(p,"name",1,80);db.tx(c->{if(one(c,"SELECT id FROM skills WHERE LOWER(name)=LOWER(?)",name)!=null)throw new IllegalArgumentException("That skill is already in the catalogue.");long sid=insert(c,"INSERT INTO skills(name) VALUES(?)",name);Workflow.audit(c,uid,"SKILL_CREATED",sid,name);return null;});redirect(x,"/admin/skills?message="+enc("Skill added."));}
                    default -> throw new HttpError(404,"This action does not exist.");
                }return;
            }
            if(path.startsWith("/admin/"))requireAdmin(admin);
            String body,title;
            switch(path){
                case "/" -> {title="Overview";body=views.dashboard(user,s.csrf());}
                case "/opportunities" -> {title="Opportunities";body=views.opportunities(user,s.csrf(),p);}
                case "/saved" -> {requireStudent(admin);title="Saved opportunities";p.put("saved","yes");body=views.opportunities(user,s.csrf(),p);}
                case "/opportunity" -> {title="Opportunity details";body=views.opportunity(user,s.csrf(),id(p,"id"));}
                case "/applications" -> {title=admin?"Application desk":"My applications";body=views.applications(user,p);}
                case "/application" -> {title="Application details";body=views.application(user,s.csrf(),id(p,"id"));}
                case "/profile" -> {title="My profile";body=views.profile(user,s.csrf());}
                case "/notifications" -> {title="Notifications";body=views.notifications(uid,s.csrf());}
                case "/analytics" -> {title="Probability lab";body=views.analytics(user,p);}
                case "/admin/opportunity" -> {title="Opportunity editor";body=views.opportunityForm(p.getOrDefault("id","").isBlank()?0:id(p,"id"),s.csrf());}
                case "/admin/students" -> {title="Student directory";body=views.students(p);}
                case "/admin/skills" -> {title="Skill catalogue";body=views.skills(s.csrf());}
                case "/admin/audit" -> {title="Activity log";body=views.audit();}
                case "/reports" -> {title="Reports";body=views.reports(admin);}
                case "/reports/applications.csv" -> {export(x,user);return;}
                default -> throw new HttpError(404,"We could not find that page.");
            }
            send(x,200,"text/html; charset=utf-8",views.layout(user,title,path,body,s.csrf(),p.getOrDefault("message","")));
        }catch(HttpError e){send(x,e.code,"text/html; charset=utf-8",Views.error(e.code,e.getMessage()));}
        catch(IllegalArgumentException|DateTimeException e){send(x,400,"text/html; charset=utf-8",Views.error(400,e.getMessage()));}
        catch(SQLException e){String ref=UUID.randomUUID().toString().substring(0,8);System.err.println("Database error "+ref+": "+e);send(x,409,"text/html; charset=utf-8",Views.error(409,"The change could not be saved. It may duplicate an existing record. Reload and try again. Reference: "+ref));}
        catch(Exception e){String ref=UUID.randomUUID().toString().substring(0,8);System.err.println("Request error "+ref);e.printStackTrace();send(x,500,"text/html; charset=utf-8",Views.error(500,"Something went wrong. Please retry. Reference: "+ref));}
        finally{x.close();}
    }
    private void saveProfile(long uid,Map<String,String> p)throws Exception{
        String name=text(p,"name",2,100),department=text(p,"department",2,100),bio=text(p,"bio",0,1500),portfolio=text(p,"portfolio",0,400);
        if(!portfolio.isBlank()){URI u=URI.create(portfolio);if(!List.of("http","https").contains(u.getScheme())||u.getHost()==null)throw new IllegalArgumentException("Portfolio must be a valid http or https address.");}
        double cgpa=decimal(p,"cgpa",0,10);int year=integer(p,"year",2000,2100);
        db.tx(c->{
            one(c,"SELECT id FROM users WHERE id=? FOR UPDATE",uid);
            update(c,"UPDATE users SET full_name=?,department=?,cgpa=?,graduation_year=?,bio=?,portfolio=? WHERE id=?",name,department,cgpa,year,bio,portfolio,uid);
            update(c,"DELETE FROM student_skills WHERE user_id=?",uid);
            for(var skill:query(c,"SELECT id FROM skills")){long sid=number(skill,"id");int level=integerDefault(p,"skill_"+sid,0,0,3);if(level>0)update(c,"INSERT INTO student_skills(user_id,skill_id,level) VALUES(?,?,?)",uid,sid,level);}
            Workflow.audit(c,uid,"PROFILE_UPDATED",uid,"Profile and skill levels updated");return null;
        });
    }
    private long saveOpportunity(long uid,Map<String,String> p)throws Exception{
        long oid=p.getOrDefault("id","").isBlank()?0:id(p,"id");String title=text(p,"title",3,150),company=text(p,"company",2,150),kind=text(p,"kind",1,20),location=text(p,"location",2,100),description=text(p,"description",20,6000),status=text(p,"status",1,10);
        if(!List.of("INTERNSHIP","PLACEMENT","FULL_TIME").contains(kind)||!List.of("OPEN","CLOSED").contains(status))throw new IllegalArgumentException("Invalid opportunity type or status.");
        int pay=integer(p,"compensation",0,100000000),year=integer(p,"year",2000,2100);double cgpa=decimal(p,"cgpa",0,10);java.sql.Date deadline=java.sql.Date.valueOf(LocalDate.parse(p.getOrDefault("deadline","")));
        if(status.equals("OPEN")&&deadline.toLocalDate().isBefore(LocalDate.now()))throw new IllegalArgumentException("An open opportunity needs a current or future deadline.");
        return db.tx(c->{
            long result=oid;
            if(oid==0)result=insert(c,"INSERT INTO opportunities(title,company,kind,location,compensation,min_cgpa,graduation_year,deadline,description,status) VALUES(?,?,?,?,?,?,?,?,?,?)",title,company,kind,location,pay,cgpa,year,deadline,description,status);
            else{if(one(c,"SELECT id FROM opportunities WHERE id=? FOR UPDATE",oid)==null)throw new HttpError(404,"Opportunity not found.");update(c,"UPDATE opportunities SET title=?,company=?,kind=?,location=?,compensation=?,min_cgpa=?,graduation_year=?,deadline=?,description=?,status=? WHERE id=?",title,company,kind,location,pay,cgpa,year,deadline,description,status,oid);update(c,"DELETE FROM opportunity_skills WHERE opportunity_id=?",oid);}
            int count=0;
            for(var skill:query(c,"SELECT id FROM skills")){long sid=number(skill,"id");int level=integerDefault(p,"skill_"+sid,0,0,3);if(level>0){count++;update(c,"INSERT INTO opportunity_skills(opportunity_id,skill_id,min_level) VALUES(?,?,?)",result,sid,level);}}
            if(count==0)throw new IllegalArgumentException("Choose at least one required skill.");
            Workflow.audit(c,uid,oid==0?"OPPORTUNITY_CREATED":"OPPORTUNITY_UPDATED",result,title);return result;
        });
    }
    private void export(HttpExchange x,Map<String,Object> u)throws Exception{
        boolean admin="ADMIN".equals(u.get("role"));String sql="SELECT a.id,u.full_name,u.email,o.title,o.company,a.status,a.score_at_apply,a.created_at FROM applications a JOIN users u ON a.user_id=u.id JOIN opportunities o ON a.opportunity_id=o.id"+(admin?"":" WHERE a.user_id=?")+" ORDER BY a.id";
        var rows=admin?db.query(sql):db.query(sql,number(u,"id"));StringBuilder csv=new StringBuilder("Application ID,Student,Email,Role,Company,Status,Skill coverage at submission,Submitted at\r\n");
        for(var row:rows){boolean first=true;for(Object v:row.values()){if(!first)csv.append(',');first=false;String cell=String.valueOf(v);if(!cell.isEmpty()&&"=+-@\t\r\n".indexOf(cell.charAt(0))>=0)cell="'"+cell;csv.append('"').append(cell.replace("\"","\"\"")).append('"');}csv.append("\r\n");}
        x.getResponseHeaders().set("Content-Disposition","attachment; filename=skillbridge-applications.csv");send(x,200,"text/csv; charset=utf-8",csv.toString());
    }
    static void requireAdmin(boolean admin){if(!admin)throw new HttpError(403,"Only the placement office can access this page.");}
    static void requireStudent(boolean admin){if(admin)throw new HttpError(403,"This action is available to student accounts.");}
    static long id(Map<String,String> p,String key){try{long n=Long.parseLong(p.getOrDefault(key,""));if(n<1)throw new NumberFormatException();return n;}catch(NumberFormatException e){throw new IllegalArgumentException("Invalid record ID.");}}
    static String text(Map<String,String> p,String key,int min,int max){String s=p.getOrDefault(key,"").trim();if(s.length()<min||s.length()>max)throw new IllegalArgumentException(key.replace('_',' ')+" must contain "+min+"–"+max+" characters.");return s;}
    static int integer(Map<String,String> p,String key,int min,int max){try{int n=Integer.parseInt(p.getOrDefault(key,""));if(n<min||n>max)throw new NumberFormatException();return n;}catch(NumberFormatException e){throw new IllegalArgumentException(key+" must be a whole number from "+min+" to "+max+".");}}
    static int integerDefault(Map<String,String> p,String key,int value,int min,int max){return !p.containsKey(key)||p.get(key).isBlank()?value:integer(p,key,min,max);}
    static double decimal(Map<String,String> p,String key,double min,double max){try{double n=Double.parseDouble(p.getOrDefault(key,""));if(!Double.isFinite(n)||n<min||n>max)throw new NumberFormatException();return n;}catch(NumberFormatException e){throw new IllegalArgumentException(key+" must be between "+min+" and "+max+".");}}
    static String email(Map<String,String> p){String e=text(p,"email",5,180).toLowerCase(Locale.ROOT);if(!e.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$"))throw new IllegalArgumentException("Enter a valid email address.");return e;}
    static Map<String,String> params(String raw){Map<String,String> p=new LinkedHashMap<>();if(raw!=null&&!raw.isEmpty())for(String pair:raw.split("&")){String[] v=pair.split("=",2);p.put(URLDecoder.decode(v[0],StandardCharsets.UTF_8),v.length==2?URLDecoder.decode(v[1],StandardCharsets.UTF_8):"");}return p;}
    static String cookie(HttpExchange x,String name){String raw=x.getRequestHeaders().getFirst("Cookie");if(raw!=null)for(String c:raw.split(";")){String[] s=c.trim().split("=",2);if(s.length==2&&s[0].equals(name))return s[1];}return null;}
    static void setCookie(HttpExchange x,String value){x.getResponseHeaders().set("Set-Cookie","SBSESSION="+value+"; Path=/; HttpOnly; SameSite=Lax"+("true".equals(System.getenv("SB_COOKIE_SECURE"))?"; Secure":"")+(value.isEmpty()?"; Max-Age=0":""));}
    static void redirect(HttpExchange x,String location)throws IOException{x.getResponseHeaders().set("Location",location);x.sendResponseHeaders(303,-1);}
    static void send(HttpExchange x,int status,String mime,String body)throws IOException{byte[] b=body.getBytes(StandardCharsets.UTF_8);x.getResponseHeaders().set("Content-Type",mime);x.sendResponseHeaders(status,b.length);x.getResponseBody().write(b);}
    static String enc(String s){return URLEncoder.encode(s,StandardCharsets.UTF_8);}
    public static final class HttpError extends RuntimeException{final int code;public HttpError(int code,String message){super(message);this.code=code;}}
}
