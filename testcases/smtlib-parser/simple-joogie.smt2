(reset)
(set-logic AUFLIA)
(declare-fun Block3538_fwd () Bool)
(declare-fun DarrSizeIdx__0 () Int)
(declare-fun r08289__0 () Int)
(declare-fun __this__0 () Int)
(declare-fun r18290__0 () Int)
(declare-fun Dparam_0__0 () Int)
(declare-fun Dnull__0 () Int)
(declare-fun Block3538_bwd () Bool)
(assert (= Block3538_fwd (and (and (and (and (and (= 0 (+ DarrSizeIdx__0 (* (- 1) (- 1) ) ) ) (= 0 (+ r08289__0 (* (- 1) __this__0 ) ) ) ) (= 0 (+ r18290__0 (* (- 1) Dparam_0__0 ) ) ) ) (= 0 (+ (ite (not (= 0 (+ r08289__0 (* (- 1) Dnull__0 ) ) ) ) 1 0 ) (* (- 1) 1 ) ) ) ) (= 0 (+ (ite (not (= 0 (+ r08289__0 (* (- 1) Dnull__0 ) ) ) ) 1 0 ) (* (- 1) 1 ) ) ) ) true ) ) )
(assert Block3538_fwd )
(assert (= Block3538_bwd (and Block3538_fwd true ) ) )
(check-sat)
(get-value (Block3538_bwd ))
(assert (not (= Block3538_bwd true ) ) )
(check-sat)
(exit)
