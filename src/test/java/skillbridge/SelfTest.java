package skillbridge;

import java.nio.file.*;
import java.sql.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static skillbridge.Database.*;

/** Runs without JUnit so the supplied offline build has no test dependency. */
public final class SelfTest {
    private static int checks;
    static void check(boolean ok,String name){checks++;if(!ok)throw new AssertionError(name);System.out.println("PASS "+name);}
    static void near(double actual,double expected,double tolerance,String name){check(Math.abs(actual-expected)<=tolerance,name);}
    @FunctionalInterface interface Throwing {void run()throws Exception;}
    static void rejects(Throwing code,String name)throws Exception{try{code.run();}catch(IllegalArgumentException|SQLException|App.HttpError e){check(true,name);return;}throw new AssertionError("Expected rejection: "+name);}
    public static void main(String[] args)throws Exception{
        near(Probability.coverage(Map.of(1L,1,2L,3),Map.of(1L,2,2L,2)),75,1e-9,"Partial proficiency coverage");
        near(Probability.coverage(Map.of(),Map.of(1L,1)),0,0,"Missing skill coverage");
        near(Probability.coverage(Map.of(),Map.of()),0,0,"Empty requirements guarded");
        var s=Probability.summarize(new double[]{10,20,30,40});near(s.mean(),25,0,"Mean");near(s.median(),25,0,"Even median");near(s.variance(),125,0,"Population variance");near(Probability.summarize(new double[]{9,1,4}).median(),4,0,"Odd median");check(Probability.summarize(new double[]{}).count()==0,"Empty statistics");
        near(Probability.empirical(new double[]{0,60,80,100},60),0.75,0,"Empirical inclusive threshold");near(Probability.betaPosteriorMean(0,0),0.5,0,"Beta prior with no data");near(Probability.betaPosteriorMean(3,8),0.4,0,"Beta posterior mean");
        near(Probability.binomial(5,2,0.2),0.2048,1e-10,"Binomial exact mass");near(Probability.binomial(5,0,0),1,0,"Binomial p zero");near(Probability.binomial(5,5,1),1,0,"Binomial p one");
        double sum=0;for(int k=0;k<=20;k++)sum+=Probability.binomial(20,k,0.37);near(sum,1,1e-10,"Binomial normalizes");
        var ci=Probability.wilson(5,10);check(ci.low()<0.5&&ci.high()>0.5&&ci.low()>=0&&ci.high()<=1,"Wilson bounds");near(Probability.wilson(0,10).low(),0,1e-10,"Wilson zero successes");
        var sim=Probability.simulate(5,0.2,100000,42);near(sim.atLeastOne(),1-Math.pow(0.8,5),0.01,"Monte Carlo agrees with analytical result");check(sim.equals(Probability.simulate(5,0.2,100000,42)),"Monte Carlo reproducible");
        rejects(()->Probability.simulate(0,0.2,1000,1),"Invalid simulation inputs");rejects(()->Probability.binomial(1,2,0.2),"Invalid k");
        String hash=Auth.hash("Testpassword123");check(Auth.verify("Testpassword123",hash),"Password verification");check(!Auth.verify("Wrongpassword123",hash),"Wrong password rejected");check(!hash.equals(Auth.hash("Testpassword123")),"Password salts are unique");rejects(()->Auth.passwordRule("short1"),"Password length");
        Auth auth=new Auth();var ticket=auth.create(42);check(auth.get(ticket.token()).userId()==42,"Session creation");var otherTicket=auth.create(42);auth.revokeUser(42);check(auth.get(ticket.token())==null&&auth.get(otherTicket.token())==null,"Password reset revokes all user sessions");auth.remove(ticket.token());check(auth.get(ticket.token())==null,"Session revocation");
        Database db=new Database("jdbc:h2:mem:tests;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000");db.initialize(Path.of("src/main/resources/schema.sql"),true);Workflow w=new Workflow(db);
        check(db.query("SELECT * FROM opportunities").size()==6,"Seed opportunities");check(db.query("SELECT * FROM skills").size()==12,"Seed skill catalogue");
        long admin=number(db.one("SELECT * FROM users WHERE role='ADMIN'"),"id"),student=number(db.one("SELECT * FROM users WHERE email='student@skillbridge.local'"),"id"),other=number(db.one("SELECT * FROM users WHERE email='rahul@skillbridge.local'"),"id");
        double score=w.score(student,1);near(score,100,1e-9,"Seeded weighted skill coverage");
        long application=w.apply(student,1,"I would like to work on reliable Java services.");check(application>0,"Application inserted");
        check(db.query("SELECT * FROM application_events WHERE application_id=?",application).size()==1,"Initial event persisted");
        rejects(()->w.apply(student,1,"Duplicate application"),"Duplicate submission blocked");
        rejects(()->w.transition(other,application,"WITHDRAWN",0,"",null,""),"Other student cannot withdraw");
        rejects(()->w.transition(admin,application,"OFFERED",0,"",null,""),"Pipeline stage cannot be skipped");
        w.transition(admin,application,"SHORTLISTED",0,"Strong profile",null,"");
        rejects(()->w.transition(admin,application,"INTERVIEW",0,"",LocalDateTime.now().plusDays(1),"Room 1"),"Stale version rejected");
        rejects(()->w.transition(admin,application,"INTERVIEW",1,"",LocalDateTime.now().minusDays(1),"Room 1"),"Past interview rejected");
        w.transition(admin,application,"INTERVIEW",1,"Technical round",LocalDateTime.now().plusDays(2),"Room 201");
        w.transition(admin,application,"OFFERED",2,"Offer issued",null,"");
        rejects(()->w.transition(admin,application,"ACCEPTED",3,"",null,""),"Admin cannot accept student's offer");
        w.transition(student,application,"ACCEPTED",3,"I accept.",null,"");
        check("ACCEPTED".equals(db.one("SELECT status FROM applications WHERE id=?",application).get("status")),"Student accepts offer");
        rejects(()->w.transition(student,application,"WITHDRAWN",4,"",null,""),"Accepted offer is terminal");
        check(db.query("SELECT * FROM application_events WHERE application_id=?",application).size()==5,"Full event timeline");
        double oldScore=((Number)db.one("SELECT score_at_apply FROM applications WHERE id=?",application).get("score_at_apply")).doubleValue();
        db.tx(c->{update(c,"UPDATE student_skills SET level=1 WHERE user_id=?",student);return null;});near(((Number)db.one("SELECT score_at_apply FROM applications WHERE id=?",application).get("score_at_apply")).doubleValue(),oldScore,0,"Application snapshot immutable after profile edit");
        db.tx(c->{update(c,"UPDATE opportunities SET status='CLOSED' WHERE id=2");return null;});rejects(()->w.apply(student,2,"Interested"),"Closed role blocked");
        db.tx(c->{update(c,"UPDATE opportunities SET deadline=? WHERE id=3",java.sql.Date.valueOf(LocalDate.now().minusDays(1)));return null;});rejects(()->w.apply(student,3,"Interested"),"Expired role blocked");
        db.tx(c->{update(c,"UPDATE opportunities SET min_cgpa=10 WHERE id=4");return null;});rejects(()->w.apply(student,4,"Interested"),"CGPA enforced");
        db.tx(c->{update(c,"UPDATE opportunities SET graduation_year=2025 WHERE id=5");return null;});rejects(()->w.apply(student,5,"Interested"),"Batch enforced");
        long withdrawn=w.apply(other,6,"Interested in SQL analysis");w.transition(other,withdrawn,"WITHDRAWN",0,"Another plan",null,"");check("WITHDRAWN".equals(db.one("SELECT status FROM applications WHERE id=?",withdrawn).get("status")),"Withdrawal workflow");
        long before=number(db.one("SELECT COUNT(*) AS n FROM notifications"),"n");try{db.tx(c->{update(c,"INSERT INTO notifications(user_id,message,link) VALUES(?,?,'/')",student,"rollback probe");throw new IllegalArgumentException("rollback");});}catch(IllegalArgumentException expected){}
        check(number(db.one("SELECT COUNT(*) AS n FROM notifications"),"n")==before,"Transaction rollback is atomic");
        rejects(()->db.tx(c->{update(c,"INSERT INTO student_skills(user_id,skill_id,level) VALUES(99999,1,2)");return null;}),"Foreign key enforced");
        rejects(()->db.tx(c->{update(c,"UPDATE users SET cgpa=12 WHERE id=?",student);return null;}),"SQL CHECK enforced");
        db.tx(c->{update(c,"UPDATE opportunities SET status='OPEN',deadline=?,min_cgpa=0,graduation_year=2027 WHERE id=2",java.sql.Date.valueOf(LocalDate.now().plusDays(5)));return null;});
        ExecutorService pool=Executors.newFixedThreadPool(2);CountDownLatch start=new CountDownLatch(1);List<Future<Boolean>> futures=new ArrayList<>();
        for(int i=0;i<2;i++)futures.add(pool.submit(()->{start.await();try{w.apply(other,2,"Concurrent test");return true;}catch(IllegalArgumentException expected){return false;}}));start.countDown();int inserted=0;for(var future:futures)if(future.get())inserted++;pool.shutdown();check(inserted==1,"Concurrent duplicate applications: exactly one commit");
        check(db.query("SELECT id FROM applications WHERE user_id=? AND opportunity_id=2",other).size()==1,"Database duplicate invariant");
        check(Views.e("<script>\"&").equals("&lt;script&gt;&quot;&amp;"),"HTML escaping");
        for(String route:List.of("overview","opportunities","profile","analytics")){Views v=new Views(db,w);String html=switch(route){case "overview"->v.dashboard(db.one("SELECT * FROM users WHERE id=?",student),"csrf");case "opportunities"->v.opportunities(db.one("SELECT * FROM users WHERE id=?",student),"csrf",Map.of());case "profile"->v.profile(db.one("SELECT * FROM users WHERE id=?",student),"csrf");default->v.analytics(db.one("SELECT * FROM users WHERE id=?",student),Map.of());};check(!html.isBlank(),"View renders: "+route);}
        System.out.println("ALL "+checks+" JAVA CHECKS PASSED");
    }
}
