package skillbridge;

import java.sql.*;
import java.nio.file.*;
import java.util.*;

/** JDBC repository. One connection per operation; multi-write workflows use tx(). */
public final class Database {
    private final String url;
    public Database(String url) { this.url = url; }
    public Connection open() throws SQLException { return DriverManager.getConnection(url, "sa", ""); }
    @FunctionalInterface public interface Work<T> { T run(Connection c) throws Exception; }
    public <T> T tx(Work<T> work) throws Exception {
        try (Connection c = open()) {
            c.setAutoCommit(false);
            try { T result = work.run(c); c.commit(); return result; }
            catch (Exception e) { c.rollback(); throw e; }
        }
    }
    public static List<Map<String,Object>> query(Connection c, String sql, Object... args) throws SQLException {
        try (PreparedStatement p = c.prepareStatement(sql)) {
            bind(p,args); try (ResultSet r = p.executeQuery()) {
                List<Map<String,Object>> rows = new ArrayList<>();
                while(r.next()) { Map<String,Object> row = new LinkedHashMap<>();
                    for(int i=1;i<=r.getMetaData().getColumnCount();i++) row.put(r.getMetaData().getColumnLabel(i).toLowerCase(Locale.ROOT),r.getObject(i));
                    rows.add(row);
                } return rows;
            }
        }
    }
    public List<Map<String,Object>> query(String sql, Object... args) throws SQLException { try(Connection c=open()) { return query(c,sql,args); } }
    public static Map<String,Object> one(Connection c,String sql,Object...args) throws SQLException {
        var rows=query(c,sql,args); return rows.isEmpty()?null:rows.get(0);
    }
    public Map<String,Object> one(String sql,Object...args) throws SQLException { try(Connection c=open()) {return one(c,sql,args);} }
    public static int update(Connection c,String sql,Object...args) throws SQLException { try(PreparedStatement p=c.prepareStatement(sql)){bind(p,args);return p.executeUpdate();} }
    public static long insert(Connection c,String sql,Object...args) throws SQLException {
        try(PreparedStatement p=c.prepareStatement(sql,Statement.RETURN_GENERATED_KEYS)){bind(p,args);p.executeUpdate();try(ResultSet r=p.getGeneratedKeys()){if(!r.next())throw new SQLException("No generated key");return r.getLong(1);}}
    }
    private static void bind(PreparedStatement p,Object[] args)throws SQLException{for(int i=0;i<args.length;i++)p.setObject(i+1,args[i]);}
    public static long number(Map<String,Object> m,String key){return ((Number)m.get(key)).longValue();}
    public void initialize(Path schema, boolean demo) throws Exception {
        try(Connection c=open()) {
            for(String sql:Files.readString(schema).split(";")) if(!sql.isBlank()) try(Statement s=c.createStatement()){s.execute(sql);}
        }
        if(demo && one("SELECT id FROM users FETCH FIRST 1 ROW ONLY")==null) seed();
    }
    private void seed() throws Exception {
        tx(c->{
            String adminPassword=System.getenv().getOrDefault("SB_ADMIN_PASSWORD","Admin@12345");
            insert(c,"INSERT INTO users(full_name,email,password_hash,role,department,cgpa,graduation_year,bio) VALUES(?,?,?,'ADMIN','Placement Office',0,2027,'')","Placement Office","admin@skillbridge.local",Auth.hash(adminPassword));
            long student=insert(c,"INSERT INTO users(full_name,email,password_hash,role,department,cgpa,graduation_year,bio) VALUES(?,?,?,'STUDENT','Computer Science',8.40,2027,?)","Ananya Rao","student@skillbridge.local",Auth.hash("Student@12345"),"Final-year student interested in backend engineering and data systems.");
            long second=insert(c,"INSERT INTO users(full_name,email,password_hash,role,department,cgpa,graduation_year,bio) VALUES(?,?,?,'STUDENT','Information Technology',7.80,2027,?)","Rahul Menon","rahul@skillbridge.local",Auth.hash("Student@12345"),"Interested in analytics and business intelligence.");
            String[] skills={"Java","SQL","Python","JavaScript","HTML/CSS","Git","Spring Boot","Data Structures","Statistics","Excel","Communication","Linux"};
            for(String skill:skills)insert(c,"INSERT INTO skills(name) VALUES(?)",skill);
            update(c,"INSERT INTO student_skills(user_id,skill_id,level) VALUES(?,1,3),(?,2,2),(?,6,2),(?,8,2),(?,11,2)",student,student,student,student,student);
            update(c,"INSERT INTO student_skills(user_id,skill_id,level) VALUES(?,2,2),(?,3,2),(?,9,2),(?,10,3)",second,second,second,second);
            String[][] jobs={
                {"Backend Engineering Intern","Meridian Systems","INTERNSHIP","Bengaluru","18000","7.0","Build Java services, write SQL queries and work with the platform engineering team. Includes code reviews and weekly mentoring.","1:2,2:2,6:1,8:2"},
                {"Data Analyst Trainee","Northstar Analytics","PLACEMENT","Chennai","450000","7.0","Prepare reliable datasets, analyse business trends and communicate findings. SQL and statistical reasoning are central to the role.","2:2,3:2,9:2,10:1"},
                {"Frontend Developer Intern","Paperplane Studio","INTERNSHIP","Remote","15000","6.5","Build accessible interfaces and collaborate with designers on responsive web products. A small portfolio is useful.","4:2,5:2,6:1"},
                {"Graduate Java Developer","Cedar Technologies","FULL_TIME","Hyderabad","650000","8.0","Join a graduate engineering programme focused on object-oriented design, relational databases and tested web services.","1:3,2:2,7:2,8:2"},
                {"Platform Support Intern","Harbor Networks","INTERNSHIP","Kochi","12000","6.0","Investigate service issues, document fixes and automate routine checks with a supportive infrastructure team.","12:2,3:1,11:2"},
                {"Business Intelligence Intern","Fieldwork Labs","INTERNSHIP","Pune","20000","7.5","Use SQL, spreadsheets and descriptive statistics to produce dependable operational reports.","2:2,9:2,10:2,11:2"}
            };
            for(String[] j:jobs){long id=insert(c,"INSERT INTO opportunities(title,company,kind,location,compensation,min_cgpa,graduation_year,deadline,description,status) VALUES(?,?,?,?,?,?,2027,?,?,'OPEN')",j[0],j[1],j[2],j[3],Integer.parseInt(j[4]),Double.parseDouble(j[5]),java.sql.Date.valueOf(java.time.LocalDate.now().plusDays(45)),j[6]);
                for(String pair:j[7].split(",")){String[] v=pair.split(":");update(c,"INSERT INTO opportunity_skills(opportunity_id,skill_id,min_level) VALUES(?,?,?)",id,Integer.parseInt(v[0]),Integer.parseInt(v[1]));}}
            update(c,"INSERT INTO notifications(user_id,message,link) VALUES(?,?,'/profile')",student,"Welcome to SkillBridge. Review your profile before applying.");
            return null;
        });
    }
}
