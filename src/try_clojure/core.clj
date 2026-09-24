(ns try-clojure.core)

(defn mean
  "Determine the arithmetic mean of the coll"
  [coll]
  (let [sum (reduce + coll)
        len (count coll)]
    (/ sum len)))

(comment
  (mean [7 3 2])
  (mean #{7 3 2})
  )
