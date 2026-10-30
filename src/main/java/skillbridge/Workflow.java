package skillbridge;

import java.sql.*;
import java.time.*;
import java.util.*;
import static skillbridge.Database.*;

/** Business invariants are enforced here, not just in browser controls. */
public final class Workflow {
    public final Database db;
    public static final Map<String,List<String>> TRANSITIONS=Map.of(
        "APPLIED",List.of("SHORTLISTED","REJECTED"),
        "SHORTLISTED",List.of("INTERVIEW","REJECTED"),
        "INTERVIEW",List.of("OFFERED","REJECTED"),
        "OFFERED",List.of("ACCEPTED","DECLINED"));
    public Workflow(Database db){this.db=db;}
    public Map<Long,Integer> levels(long id,boolean student)throws SQLException{Map<Long,Integer> result=new LinkedHashMap<>();for(var r:db.query(student?"SELECT skill_id,level AS proficiency FROM student_skills WHERE user_id=?":"SELECT skill_id,min_level AS proficiency FROM opportunity_skills WHERE opportunity_id=?",id))result.put(number(r,"skill_id"),((Number)r.get("proficiency")).intValue());return result;}
    public double score(long student,long opportunity)throws SQLException{return Probability.coverage(levels(student,true),levels(opportunity,false));}
    public static String eligibility(Map<String,Object> student,Map<String,Object> job){
        if(!"OPEN".equals(job.get("status")))return "This opportunity is closed.";
        if(((java.sql.Date)job.get("deadline")).toLocalDate().isBefore(LocalDate.now()))return "The application deadline has passed.";
        if(((Number)student.get("cgpa")).doubleValue()<((Number)job.get("min_cgpa")).doubleValue())return "Your CGPA is below the eligibility requirement.";
        if(number(student,"graduation_year")!=number(job,"graduation_year"))return "This role is for the "+job.get("graduation_year")+" graduating batch.";
        return "";
    }
    public long apply(long user,long opportunity,String letter)throws Exception{
        if(letter.isBlank()||letter.length()>3000)throw new IllegalArgumentException("Add a cover note of 1–3000 characters.");
        return db.tx(c->{
            var u=one(c,"SELECT * FROM users WHERE id=? AND role='STUDENT' FOR UPDATE",user);
            var job=one(c,"SELECT * FROM opportunities WHERE id=? FOR UPDATE",opportunity);
            if(u==null||job==null)throw new IllegalArgumentException("Student or opportunity not found.");
            String reason=eligibility(u,job);if(!reason.isEmpty())throw new IllegalArgumentException(reason);
            if(one(c,"SELECT id FROM applications WHERE user_id=? AND opportunity_id=?",user,opportunity)!=null)throw new IllegalArgumentException("You have already applied to this opportunity. Check My applications.");
            Map<Long,Integer> owned=new LinkedHashMap<>(),required=new LinkedHashMap<>();
            for(var r:query(c,"SELECT skill_id,level FROM student_skills WHERE user_id=?",user))owned.put(number(r,"skill_id"),((Number)r.get("level")).intValue());
            for(var r:query(c,"SELECT skill_id,min_level FROM opportunity_skills WHERE opportunity_id=?",opportunity))required.put(number(r,"skill_id"),((Number)r.get("min_level")).intValue());
            String snapshot="Name: "+u.get("full_name")+"; Department: "+u.get("department")+"; CGPA: "+u.get("cgpa")+"; Batch: "+u.get("graduation_year")+"; Portfolio: "+u.get("portfolio")+"; Skill levels (catalogue IDs): "+owned;
            long id=insert(c,"INSERT INTO applications(user_id,opportunity_id,status,cover_letter,score_at_apply,profile_snapshot) VALUES(?,?,'APPLIED',?,?,?)",user,opportunity,letter,Probability.coverage(owned,required),snapshot);
            event(c,id,user,"APPLIED","Application submitted. Profile and skill score captured at submission.");
            notify(c,user,"Application submitted to "+job.get("company"),"/application?id="+id);
            audit(c,user,"APPLICATION_SUBMITTED",id,"Opportunity #"+opportunity);return id;
        });
    }
    public void transition(long actor,long application,String next,int version,String note,LocalDateTime interview,String location)throws Exception{
        if(note.length()>1000||location.length()>300)throw new IllegalArgumentException("The note or location is too long.");
        db.tx(c->{
            var u=one(c,"SELECT * FROM users WHERE id=?",actor);
            var a=one(c,"SELECT * FROM applications WHERE id=? FOR UPDATE",application);
            if(u==null||a==null)throw new IllegalArgumentException("Application not found.");
            boolean admin="ADMIN".equals(u.get("role")),owner=number(a,"user_id")==actor;
            String old=a.get("status").toString();
            boolean valid=admin?TRANSITIONS.getOrDefault(old,List.of()).contains(next)&&!List.of("ACCEPTED","DECLINED").contains(next):owner&&((next.equals("WITHDRAWN")&&List.of("APPLIED","SHORTLISTED","INTERVIEW").contains(old))||(old.equals("OFFERED")&&List.of("ACCEPTED","DECLINED").contains(next)));
            if(!valid)throw new IllegalArgumentException("That status change is not permitted.");
            if(next.equals("INTERVIEW")&&(interview==null||interview.isBefore(LocalDateTime.now())||location.isBlank()))throw new IllegalArgumentException("Set a future interview time and a venue or meeting link.");
            int changed=update(c,"UPDATE applications SET status=?,version=version+1,updated_at=CURRENT_TIMESTAMP,interview_at=COALESCE(?,interview_at),interview_location=CASE WHEN ?='' THEN interview_location ELSE ? END WHERE id=? AND version=?",next,interview,location,location,application,version);
            if(changed!=1)throw new IllegalArgumentException("This application changed in another window. Refresh and try again.");
            event(c,application,actor,next,note.isBlank()?"Status updated to "+next:note);
            notify(c,number(a,"user_id"),"Application #"+application+" is now "+next.toLowerCase(Locale.ROOT)+".","/application?id="+application);
            audit(c,actor,"STATUS_CHANGED",application,old+" → "+next);return null;
        });
    }
    public static void event(Connection c,long application,long actor,String status,String note)throws SQLException{update(c,"INSERT INTO application_events(application_id,actor_id,status,note) VALUES(?,?,?,?)",application,actor,status,note);}
    public static void notify(Connection c,long user,String message,String link)throws SQLException{update(c,"INSERT INTO notifications(user_id,message,link) VALUES(?,?,?)",user,message,link);}
    public static void audit(Connection c,long actor,String action,long entity,String detail)throws SQLException{update(c,"INSERT INTO audit_log(actor_id,action,entity_id,detail) VALUES(?,?,?,?)",actor,action,entity,detail);}
}
