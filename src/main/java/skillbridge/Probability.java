package skillbridge;

import java.util.*;

/** Explicitly separates prerequisite coverage from observed recruitment outcomes. */
public final class Probability {
    private Probability() {}
    public record Summary(int count,double mean,double median,double variance,double standardDeviation) {}
    public record Interval(double low,double high) {}
    public record Simulation(double meanOffers,double atLeastOne,double theoreticalAtLeastOne) {}
    public static double coverage(Map<Long,Integer> owned,Map<Long,Integer> required){
        if(required.isEmpty())return 0;
        double sum=0;for(var e:required.entrySet())sum+=Math.min(1.0,owned.getOrDefault(e.getKey(),0)/(double)e.getValue());
        return 100.0*sum/required.size();
    }
    public static Summary summarize(double[] input){
        if(input.length==0)return new Summary(0,0,0,0,0);
        double[] a=input.clone();Arrays.sort(a);double mean=Arrays.stream(a).average().orElse(0),variance=0;
        for(double x:a)variance+=(x-mean)*(x-mean);variance/=a.length;
        return new Summary(a.length,mean,a.length%2==1?a[a.length/2]:(a[a.length/2-1]+a[a.length/2])/2,variance,Math.sqrt(variance));
    }
    public static double empirical(double[] scores,double threshold){return scores.length==0?0:Arrays.stream(scores).filter(s->s>=threshold).count()/(double)scores.length;}
    public static double betaPosteriorMean(int successes,int trials){validate(successes,trials);return (successes+1.0)/(trials+2.0);}
    public static Interval wilson(int successes,int trials){validate(successes,trials);if(trials==0)return new Interval(0,1);double z=1.959963984540054,p=successes/(double)trials,d=1+z*z/trials,c=(p+z*z/(2*trials))/d,h=z*Math.sqrt(p*(1-p)/trials+z*z/(4.0*trials*trials))/d;return new Interval(Math.max(0,c-h),Math.min(1,c+h));}
    public static double binomial(int n,int k,double p){if(n<0||n>500||k<0||k>n||!Double.isFinite(p)||p<0||p>1)throw new IllegalArgumentException("Invalid binomial parameters");if(p==0)return k==0?1:0;if(p==1)return k==n?1:0;double log=0;for(int i=1;i<=k;i++)log+=Math.log(n-i+1)-Math.log(i);return Math.exp(log+k*Math.log(p)+(n-k)*Math.log1p(-p));}
    public static Simulation simulate(int applications,double p,int trials,long seed){if(applications<1||applications>100||!Double.isFinite(p)||p<0||p>1||trials<1||trials>100000)throw new IllegalArgumentException("Invalid simulation parameters");Random random=new Random(seed);long total=0,any=0;for(int i=0;i<trials;i++){int successes=0;for(int j=0;j<applications;j++)if(random.nextDouble()<p)successes++;total+=successes;if(successes>0)any++;}return new Simulation(total/(double)trials,any/(double)trials,1-Math.pow(1-p,applications));}
    private static void validate(int s,int n){if(n<0||s<0||s>n)throw new IllegalArgumentException("Invalid counts");}
}
