(ns asparagus.boot.red
  (:refer-clojure :exclude [> <])
  (:require [asparagus.boot.prelude :as p
             :refer [$ cp is]]
            [clojure.core :as c]))

(do :protos

 (defprotocol IMergeInto (-merge-into [x y]))
 (defprotocol IConcretize (-concretize [x y]))
 (defprotocol ICombine (-combine [x y]))
 (declare > <))

(do :abstraction

    (defrecord Abstraction [val]
      clojure.lang.IFn
      (invoke [x y] (> y x)))

    (defn abstraction? [x] (instance? Abstraction x))

    (defn abstraction [val]
      (Abstraction. val))

    (def A abstraction)

    (defn level [x]
      (if (abstraction? x)
        (inc (level (:val x)))
        0))

    (defn deep-val [x]
      (if (abstraction? x)
        (deep-val (:val x))
        x)))

(do :><

    (defmacro defn+
      "behave the same as defn but will define also applied,
       abstracted and underscore variations (see tutorial)"
      [name & body]
      (let [name* (p/sym name '*)
            name_ (p/sym name '_)
            name_* (p/sym name_ '*)
            §name (p/sym '§ name)
            §name_ (p/sym §name '_)
            def* (fn [n] `(def ~(p/sym n '*) (partial apply ~n)))
            def_ (fn [n] `(defn ~(p/sym n '_) [& xs#] (A #(~(p/sym n '*) % xs#))))]
        `(do (defn ~name ~@body )
             ~(def* name)
             ~(def_ name)
             ~(def* name_)
             (defn ~§name [x# & xs#] (~name* x# (map A xs#)))
             ~(def* §name)
             ~(def_ §name)
             ~(def* §name_))))

    (defn+ >
      ([x] x)
      ([x y]
       (let [lx (level x)
             ly (level y)]
         (cond
           (satisfies? ICombine x) (-combine x y)
           (= lx ly) (-merge-into y x)
           (= lx (dec ly)) (-concretize y x)
           (c/> lx ly) (> x (A y))
           (c/< lx ly) (> (A x) y))))
      ([x y & ys]
       (loop [nxt (> x y) ys ys]
         (if (and nxt (seq ys))
           (recur (> nxt (first ys)) (next ys))
           nxt))
       #_(reduce > (> x y) ys)))

    (defn+ <
      ([x] nil)
      ([x y & xs]
       (or (> x y)
           (apply < x xs))))



    )

(do :prim+

    (defn- zipmaps
      [f m1 m2]
      (reduce
       (fn [ret k]
         (assoc ret k (f (get m1 k) (get m2 k))))
       m1 (into (set (keys m1)) (keys m2))))

    #?(:clj
       (extend-protocol IMergeInto

         nil
         (-merge-into [_ x] x)

         Object
         (-merge-into [y _] y)

         clojure.lang.Seqable
         (-merge-into [y x] (concat x y))

         clojure.lang.IPersistentVector
         (-merge-into [y x] (mapv > x y))

         clojure.lang.IPersistentMap
         (-merge-into [y x] (zipmaps > x y))

         clojure.lang.IPersistentSet
         (-merge-into [y x] (if x (into x y) y))

         clojure.lang.Fn
         (-merge-into [y x] (if x (comp x y) y)))

       :cljs
       (extend-protocol IMergeInto
         nil
         (-merge-into [_ x] x)
         default
         (-merge-into [y _] y)
         function
         (-merge-into [y x] (if x (comp x y) y))
         object
         (-merge-into [y x]
           (cond (not (coll? y)) y
                 (map? y) (zipmaps > x y)
                 (vector? y) (mapv > x y)
                 (seq? y) (concat x y)
                 (set? y) (into y x)))))

    #?(:clj
       (extend-protocol IConcretize

         nil
         (-concretize [_ x] x)

         Object
         (-concretize [y x] y)

         clojure.lang.Fn
         (-concretize [y x] (y x))

         clojure.lang.Seqable
         (-concretize [y x]
           (>* x y))

         clojure.lang.IPersistentVector
         (-concretize [y x]
           (mapv (partial > x) y))

         clojure.lang.IPersistentMap
         (-concretize [y x]
           #_(println 'concmap y x)
           (p/$vals y (fn [z] (> x z))))

         clojure.lang.IPersistentSet
         (-concretize [y x]
           (into #{} (map (partial > x) y))))

       :cljs
       (extend-protocol IConcretize
         nil
         (-concretize [_ x] x)
         default
         (-concretize [y _] y)
         function
         (-concretize [y x] (y x))
         object
         (-concretize [y x]
           (if (coll? y)
             (let [k #(-concretize % x)]
               (cond
                 (map? y) (u/map-vals k y)
                 (vector? y) (mapv k y)
                 (seq? y) (map k y)
                 (set? y) (into #{} (map k y))))
             y)))))

(do :abstraction+

    (extend-type Abstraction
      IMergeInto
      (-merge-into [y x]
        (A (> (:val x) (:val y))))
      IConcretize
      (-concretize [{vy :val} x]
        #_(p/prob 'concretize y x)
        (if (abstraction? vy)
          (A (-concretize vy (:val x)))
          (-concretize vy x))))

    (defmethod clojure.pprint/simple-dispatch Abstraction [e]
      (clojure.pprint/simple-dispatch
       (list (apply p/sym (repeat (level e) '§))
             (deep-val e))
       #_(let [lvl (level e)]
            (if (= 1 lvl)
              (list 'A  (:val e))
              (list (p/sym 'A (str lvl)) (deep-val e))))))

    )

(do :macros

    (comment
      :by-hand
      (defmacro §
        ([v] `(A ~v))
        ([pat & body]
         `(§ (fn [~pat] ~@body))))
      (defmacro §_
        [& body]`(§ ~'_ ~@body))
      (defmacro §§
        ([v] `(A (A ~v)))
        ([pat & body]
         `(§§ (fn [~pat] ~@body))))
      (defmacro §§_
        [& body]`(§§ ~'_ ~@body))
      (defmacro §§§
        ([v] `(A (A (A ~v))))
        ([pat & body]
         `(§§§ (fn [~pat] ~@body))))
      (defmacro §§§_
        [& body]`(§§§ ~'_ ~@body))
      (defmacro def§
        [name & xs]
        `(def ~name (§ ~@xs)))
      (defmacro def§_
        [name & xs]
        `(def ~name (§_ ~@xs)))
      (defmacro def§§
        [name & xs]
        `(def ~name (§§ ~@xs)))
      (defmacro def§§_
        [name & xs]
        `(def ~name (§§_ ~@xs)))
      (defmacro def§§§
        [name & xs]
        `(def ~name (§§§ ~@xs)))
      (defmacro def§§§_
        [name & xs]
        `(def ~name (§§§_ ~@xs))))

    (defmacro decline-macros [nam from-fn]
      (let [nam_ (p/sym nam '_)
            defnam (p/sym "def" nam)
            defnam_ (p/sym defnam '_)]
        `(do
           (defmacro ~nam
             ([x#] `(~'~from-fn ~x#))
             ([pat# & body#]
              `(~'~nam (fn [~pat#] ~@body#))))
           (defmacro ~nam_
             [& body#]`(~'~nam ~'~'_ ~@body#))
           (defmacro ~defnam
             [name# & xs#]
             `(def ~name# (~'~nam ~@xs#)))
           (defmacro ~defnam_
             [name# & xs#]
             `(def ~name# (~'~nam_ ~@xs#))))))

    (decline-macros § A)
    (decline-macros §§ (> A A))
    (decline-macros §§§ (> A A A))
    (decline-macros guard
      (fn [f] (§_ (and (or (f _) nil) _))))

    (defn else [x] (§_ (or _ x)))
    (defn k [x] (§ (constantly x)))
    (def default else)
    (def overide k)

    (defmacro import-fns [& xs]
      `(do ~@(map (fn [s] `(def ~(p/sym '§ (name s)) (A ~(p/qualified-sym s)))) xs)))

    (defmacro import-preds [& xs]
      `(do ~@(map (fn [s] `(def ~(p/sym '§ (name s)) (guard ~(p/qualified-sym s)))) xs)))

    (comment

      (> 1 (§ inc))
      (> 1 (> (§ x (p/prob 'a x) (inc x))
                (§§ a (p/prob 'b a) [a a])))

      (> 1 (§ [inc inc]))

      (> 1 (> (§ inc) (§ inc)))

      (> 1 (> (§ inc) (§_ [_ _])))
      ((> 12 (> inc (§§ u u))) 1)

      )

    (defn macrocall? [[s & _]]
      (and (symbol? s)
           (:macro (meta (resolve  s)))))

    (defn red-fn [e]
      (cp e
          seq?
          (if (macrocall? e)
            (cons (first e) ($ (next e) red-fn))
            (cons `> ($ e red-fn)))
          p/holycoll? ($ e red-fn)
          e))

    (defmacro red [& xs]
      (red-fn xs))

    #_(red
       (1 (§ (inc inc))))

    #_(red 1 ((§ [(§ inc) (§ inc)]) (§§_ {:l _ :r _}))))

(do :maps

 (defn- split-kvs [xs]
   (loop [taken {} [x y & xs :as remains] xs]
     (if (keyword? x)
       (recur (assoc taken x y) xs)
       [taken remains])))

 (defn hm
   "like & except that it can take a flat series of key values before updates
   (hm :a 1 :b 2 {:c 3} ...) <=> (& {:a 1 :b 2} {:c 3} ...)"
   ([] {})
   ([x] x)
   ([x y & ys]
    (if (keyword? x)
      (let [[m rs] (split-kvs ys)
            base (assoc m x y)]
        (if rs (>* base rs) base))
      (>* x y ys))))

 (def hm* (partial apply hm)))

(do :tutorial

    (do :abstraction

        (def id (A identity))
        (def §inc (A inc))
        (def §dec (A dec))

        #_(> 2 (> §inc 1))
        #_(> 2 (§ 3))
        #_(> 2 (> 1 (§ id)))
        #_(> 2 (> 1 (§§ [id 42]))))

    (do :base
        ;; Red stands for reduce
        ;; A red expression can be viewed like a threading expression, data flows from left to right
        ;; In red we create "functions" (represented by the Abastraction record) like this
        ;; since red functions takes only one argument we can remove the vector around it

        (§ x (+ x x))

        ;; we can destructure like in clojure

        (§ {:keys [a]} (+ a a))

        ;; If we want to use it we have to thread something into it

        (is 6
            (> 3 (§ x (+ x x))))

        (is 84
            (> {:a 42}
               (§ {:keys [a]} (+ a a))))

        ;; We can pipe several functions in a > expression

        (is 12
            (> 3
               (§ x (+ x x))
               (§ x (+ x x))))


        ;; In this exemple 2 is in 'function position' so it act has is (returning itself)

        (is 2
            (> 1 2))

        ;; constants

        (is 'iop (> 1 'iop))
        (is :iop (> 1 :iop))
        (is "Yo" (> 1 "Yo"))

        ;; collections

        ;; list do concatenation
        (is '(1 2 3 4)
            (> '(1 2) '(3 4)))

        ;; sets do union
        (is #{1 2 3 4}
            (> #{1 2} #{3 4}))

        ;; vector do idexed merging
        (is [3 4]
            (> [1 2] [3 4]))

        ;; map do merging
        (is {:a 1 :b 4 :c 0}
            (> {:a 1 :b 2} {:c 0 :b 4}))

        ;; functions can go into collections

        (is {:a 2 :b 1}
            (> {:a 1 :b 1}
               {:a (§ x (+ x x))}))

        (is [1 0]
            (> [0 0] [(§ inc) nil]))

        ;; nil is a no op

        (is 1 (> 1 nil) (> nil 1))

        ;; nested collection

        (is (> {:a {:b [1 2 3] :c '(1 2)}
                :foo 'bar}
               {:a {:b [nil (§ inc) nil] :c '(3)}})

            {:a {:b [1 3 3],
                 :c '(1 2 3)}
             :foo 'bar}))

    (do :functions

        ;; functions
        ;; Has we just seen in the previous exemple, the § form can take a clojure function

        (is 2
            (> 1 (§ inc)))

        ;; But it can take datastructures to

        (is [-1 42 1]
            (> 0 (§ [(§ dec) 42 (§ inc)])))

        ;; nested

        (is {:ret {:a 2, :b [0 -1]}}
            (> {:a 1 :b [0 0]}
               (§ {:ret {:a (§ inc) :b [nil (§ dec)]}})))

        ;; composed 
        (is (> 10 (> (§ {:dec §dec
                         :inc §inc})
                     (§ {:id nil
                         :neg (k (§ -))
                         })))
            {:dec 9, :inc 11,
             :neg -10, :id 10}))

    (do :defs

        ;; def§

        (def§ negate x (- x))

        (def§ prob x (println x) x)

        (is -1
            (> 1 negate)))

    (do :default-overside
        ;; default and overide

        (> nil (default 12))

        (> '(1 2 3) (overide '(2 3 4)))

        ;; 'default is also named 'else and 'overide 'k

        (> nil
           (§ {:a (k '(a b c))
               :b (else {:a 2})
               :c (>_ (else 0) §inc)}))

        (> nil (§ 12)))

    (do :guards
        ;; guards and shortcircuiting

        ;; guards returns their argument on success or nil otherwise
        (> 1 (guard pos?) §inc)

        (> -1
           (guard pos?) ;; this fails so the whole form is shortcircuited
           prob         ;; never called
           §dec         ;; neither
           )

        (< 1
           (>_ (guard pos?) :pos)
           (guard neg?)
           (>_ (guard zero?) :zero!))

        (let [f (<_
                 (>_ (guard pos?) :pos)
                 (guard neg?)
                 (>_ (guard zero?) :zero!))]
          (is :pos (f 1) (> 1 f))
          (is -1 (f -1) (> -1 f))
          (is :zero! (f 0) (> 0 f))))

    (do :importing 
        ;; importing

        (import-preds zero? vector?)
        (import-fns reverse shuffle)

        (is [1 2]
            (> [1 2] §vector?))

        (> [1 2 3] §shuffle))

    (do :variations

        ;; an applied version of >
        ;; (is to > what  core/list* is to core/list)
        (is 3
            (>* 1 [§inc §inc])
            (>* 1 §inc [§inc])
            (> 1 §inc §inc))

        ;; underscore version
        (is 3
            (> 1 §inc §inc)
            ((>_ §inc §inc) 1)
            ((>_* [§inc §inc]) 1))

        ;; abstracted >
        ;; all n+1 arguments will be abstracted before being fed to >
        (is 3
            (§> 1 inc inc)
            (> 1 §inc §inc))

        ;; variations
        (is 3
            (§>* 1 [inc inc])
            ((§>_ inc inc) 1)
            ((§>_* [inc inc]) 1))

        

        )

    )

