# Probability and statistics

## Skill prerequisite coverage

For each required skill s, minimum proficiency is $r_s\in\{1,2,3\}$; profile proficiency is $\ell_s\in\{0,1,2,3\}$ (0 means absent). For m required skills:

$$C=\frac{100}{m}\sum_{s=1}^{m}\min(1,\ell_s/r_s).$$

Each listed skill has equal weight, partial proficiency receives proportional credit, and surplus proficiency is capped. With no requirements the app returns 0% (conservative convention for undefined comparison). Coverage is frozen at submission. Eligibility and coverage are separate: students who meet academic rules may apply even with skills gaps.

**Example:** Java required at 2, SQL at 2, Git at 1; student Java 3, SQL 1, Git absent: $100(1+0.5+0)/3=50\%$. This is not the chance of a job offer.

## Population statistics

The current population is each registered student × each role open with deadline today or later; the student view scopes to that profile × all open roles. Academically ineligible pairs remain in the displayed score distribution; eligibility is another gate. Let $x_1,\ldots,x_N$ be coverage scores. The mean is $\bar x=\sum x_i/N$, median is the ordered middle value (average the two middle values for even N), population variance is $\sigma^2=\sum(x_i-\bar x)^2/N$, and standard deviation is $\sqrt{\sigma^2}$. N is used rather than N−1 because the scope is the complete database population selected. For threshold t, empirical frequency is $\#\{i:x_i\ge t\}/N$ (inclusive). If N=0, no rate is reported.

Example [25,50,75,100]: mean 62.5, median 62.5, variance 781.25, standard deviation about 27.95, and $P(C\ge60\%)=2/4=50\%$.

## Observed outcomes

A decided record is offered, accepted, declined or rejected. Offer event is offered, accepted or declined. This raw rate describes the *selected decided records*, not a candidate: unresolved applications are excluded; outcomes may be confounded by company/role/cohort/selection, repeat applicants and missing updates. Independence and common probability assumptions need not hold.

For s offer events among n decided applications, raw fraction is s/n. A Beta(1,1) prior yields Beta$(s+1,n-s+1)$ and mean $(s+1)/(n+2)$. With no outcomes, this is 0.5 from the **prior only**, not evidence. The two-sided 95% Wilson interval is an approximate frequentist binomial interval, not a 95% probability a particular student gets an offer. For zero decisions the UI reports no Wilson estimate. Neither statistic corrects selection bias or creates a personal hiring prediction.

## Hypothetical binomial and simulation

Assume $X\sim\mathrm{Binomial}(n,p)$ for n independent trials with identical *hypothetical, user-entered* offer rate p:

$$P(X=k)=\binom{n}{k}p^k(1-p)^{n-k},\quad E[X]=np,\quad P(X\ge1)=1-(1-p)^n.$$

Coverage does not estimate p. Seeded Monte Carlo estimates average offers and at-least-one rate over selected trials; the seed makes reruns reproducible, while finite simulation remains noisy. The exact calculation is shown beside it.

## Code/test map

`Probability.java` implements skill coverage, statistics, threshold frequency, Beta mean, Wilson interval, log-space binomial PMF and seeded simulation. `SelfTest.java` covers empty and missing skills, partial/excess proficiency, median variants, threshold inclusivity, prior-only outcome, p=0/1, normalization, interval bounds, invalid inputs and Monte Carlo/analytic agreement. Run `sh test.sh` or `test.bat`.