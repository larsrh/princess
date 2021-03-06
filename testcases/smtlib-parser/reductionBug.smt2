(set-option :produce-models true)
(set-option :produce-interpolants false)
(set-logic AUFNIRA)
(set-info :source |
    SMT script generated on 2015/04/07 by Ultimate. http://ultimate.informatik.uni-freiburg.de/
|)
(set-info :smt-lib-version 2.0)
(set-info :category "industrial")
(declare-fun |c___VERIFIER_nondet_int_#res| () Int)
(declare-fun |c___VERIFIER_nondet_int_#res_primed| () Int)
(declare-fun |c_ULTIMATE.start_#t~ret5| () Int)
(declare-fun |c_ULTIMATE.start_#t~ret5_primed| () Int)
(declare-fun |c_main_#res| () Int)
(declare-fun |c_main_#res_primed| () Int)
(declare-fun |c_main_#t~nondet2| () Int)
(declare-fun |c_main_#t~nondet2_primed| () Int)
(declare-fun |c_main_#t~nondet3| () Int)
(declare-fun |c_main_#t~nondet3_primed| () Int)
(declare-fun |c_main_#t~ret4| () Int)
(declare-fun |c_main_#t~ret4_primed| () Int)
(declare-fun c_main_~m~7 () Int)
(declare-fun c_main_~m~7_primed () Int)
(declare-fun c_main_~n~7 () Int)
(declare-fun c_main_~n~7_primed () Int)
(declare-fun c_main_~z~7 () Int)
(declare-fun c_main_~z~7_primed () Int)
(declare-fun |c_gcd_#in~y1| () Int)
(declare-fun |c_gcd_#in~y1_primed| () Int)
(declare-fun |c_gcd_#in~y2| () Int)
(declare-fun |c_gcd_#in~y2_primed| () Int)
(declare-fun |c_gcd_#res| () Int)
(declare-fun |c_gcd_#res_primed| () Int)
(declare-fun |c_gcd_#t~ret0| () Int)
(declare-fun |c_gcd_#t~ret0_primed| () Int)
(declare-fun |c_gcd_#t~ret1| () Int)
(declare-fun |c_gcd_#t~ret1_primed| () Int)
(declare-fun c_gcd_~y1 () Int)
(declare-fun c_gcd_~y1_primed () Int)
(declare-fun c_gcd_~y2 () Int)
(declare-fun c_gcd_~y2_primed () Int)
(assert (and (= (+ (* 3 c_gcd_~y2) |c_gcd_#in~y1|) (+ (* 3 |c_gcd_#in~y2|) c_gcd_~y1)) (<= |c_gcd_#in~y2| c_gcd_~y2)))
(assert (not (and (= (+ (* 3 c_gcd_~y2) |c_gcd_#in~y1|) (+ (* 3 |c_gcd_#in~y2|) c_gcd_~y1)) (<= |c_gcd_#in~y2| c_gcd_~y2) (= (+ (* 2 c_gcd_~y2) |c_gcd_#in~y1|) (+ (* 2 |c_gcd_#in~y2|) c_gcd_~y1)) (= |c_gcd_#in~y1| (* 2 |c_gcd_#in~y2|)) (<= |c_gcd_#in~y2| |c_gcd_#t~ret0|) (= (+ c_gcd_~y2 |c_gcd_#in~y1|) (+ |c_gcd_#in~y2| c_gcd_~y1)) (<= |c_gcd_#in~y1| c_gcd_~y1) (= (+ (* 4 c_gcd_~y2) |c_gcd_#in~y1|) (+ (* 4 |c_gcd_#in~y2|) c_gcd_~y1)) (or (<= (+ |c_gcd_#in~y1| 3) c_gcd_~y1) (and (and (<= (+ (* 4 c_gcd_~y2) (* 5 |c_gcd_#in~y1|)) (+ (* 5 c_gcd_~y1) (* 4 |c_gcd_#in~y2|))) (<= (+ (* 4 |c_gcd_#in~y2|) (* 5 c_gcd_~y1)) (+ (* 4 c_gcd_~y2) (* 5 |c_gcd_#in~y1|) 2))) (<= (+ c_gcd_~y1 |c_gcd_#in~y2|) (+ c_gcd_~y2 |c_gcd_#in~y1|)))))))
(check-sat)
