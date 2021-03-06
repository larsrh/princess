
(declare-datatypes ((Tree 0)) (
   ((leaf) (node (data Int) (left Tree) (right Tree)) )))

(declare-fun x () Tree)
(declare-fun y () Int)
(declare-fun y2 () Int)
(declare-fun z () Tree)

(push 1)
(assert (= x (node 1 leaf z)))
(check-sat)
(assert (= z (node 42 leaf leaf)))
(check-sat)
(get-value ((data (right x))))
(assert (< (data (right x)) 0))
(check-sat)
(pop 1)

(push 1)
(assert (= x (node 1 (node y leaf leaf) (node y2 leaf leaf))))
(assert (= (left x) (right x)))
(assert (> y 42))
(check-sat)
(get-value (y y2))
(pop 1)

(set-option :produce-interpolants true)

(push 1)
(assert (! (= x (node 1 leaf z)) :named left))
(assert (! (= x (node 2 leaf leaf)) :named right))
(check-sat)
(get-interpolants left right)
(pop 1)

(push 1)
(assert (! (= x (node 1 leaf (node y leaf leaf))) :named left))
(assert (! (= x (node 1 leaf leaf)) :named right))
(check-sat)
(get-interpolants left right)
(pop 1)
