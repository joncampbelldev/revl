(ns revl.core
  (:require [seesaw.core :as sc]
            [seesaw.table :as st]
            [clojure.core.async :as async]
            [com.hypirion.clj-xchart :as xc])
  (:import (java.util Date UUID)
           (java.text SimpleDateFormat)
           (javax.swing JTable)
           (java.awt.event MouseEvent)
           (java.awt Point)))

(defn sprintln [& args]
  (locking *out*
    (apply println args)))

(defn array? [x] (.isArray (class x)))

(defn coll-not-map? [x] (and (or (coll? x)
                                 (array? x))
                             (not (map? x))))

(defmulti data->table-model
          (fn [data]
            (cond
              (map? data) :map
              (and (coll-not-map? data) (every? map? data)) :maps
              (and (coll-not-map? data) (every? coll-not-map? data)) :colls
              (coll-not-map? data) :coll
              :else :bean)))

(defmethod data->table-model :map [key->value]
  (let [rows (map #(vector % (get key->value %)) (keys key->value))]
    {:columns ["property" "value"] :rows rows}))

(defmethod data->table-model :maps [maps]
  (let [maps (vec maps)
        ks (distinct (mapcat keys maps))]
    {:columns ks
     :rows (map (fn [item]
                  (map #(get item %) ks))
                maps)}))

(defmethod data->table-model :colls [colls]
  (let [colls (vec colls)
        ks (range (apply max (map count colls)))]
    {:columns ks
     :rows (map (fn [item]
                  (map #(get item %) ks))
                colls)}))

(defmethod data->table-model :coll [items]
  {:columns ["index" "item"]
   :rows (map-indexed vector items)})

(defmethod data->table-model :bean [item]
  (data->table-model (bean item)))

(defn point->tuple [^Point point]
  [(.-x point) (.-y point)])

(defn tuple->point [[x y]]
  (new Point x y))

(defn show! [{:keys [component on-close location title]}]
  (let [frame (sc/frame :title (or title "Data")
                        :on-close :dispose
                        :content (sc/scrollable component))]
    (when location
      (.setLocation frame (tuple->point location)))
    (when on-close
      (sc/listen frame :window-closed on-close))
    (-> frame (sc/pack!) (sc/show!))))

(declare data-table)

(def useful-first-columns #{"id" "name" "index" "property"
                            :id :name :index :property})

(defn show-cell-data-as-table [origin-table columns row-index column-index]
  (when (and (not (neg? row-index))
             (not (neg? column-index)))
    (let [row-data (st/value-at origin-table row-index)
          column-key (nth columns column-index)
          cell-data (get row-data column-key)]
      (when (and (not (nil? cell-data))
                 (not (number? cell-data))
                 (not (string? cell-data))
                 (not (keyword? cell-data)))
        (let [[x y] (-> origin-table (.getLocationOnScreen) (point->tuple))
              first-column (first columns)
              title (if (contains? useful-first-columns first-column)
                      (str first-column " " (get row-data first-column) " " column-key)
                      column-key)]
          (show! (merge (data-table cell-data) {:title title :location [(+ 20 x) (+ 20 y)]})))))))

(defn mouse-event->table-coord [^MouseEvent e table]
  (let [row-index (.rowAtPoint table (.getPoint e))
        column-index (.columnAtPoint table (.getPoint e))]
    [row-index column-index]))

(defn data-table
  ([data] (data-table data {}))
  ([data {:keys [column-labels]}]
   (let [{:keys [rows columns]} (data->table-model data)
         columns (mapv #(if (keyword? %) % (keyword (str %))) (or column-labels columns))
         rows (mapv vec rows)
         ^JTable table (sc/table :model [:columns columns :rows rows])]
     (sc/listen table
                :mouse-clicked
                (fn [e]
                  (let [[row-index column-index] (mouse-event->table-coord e table)]
                    (show-cell-data-as-table table columns row-index column-index))))
     {:component table})))

(def standard-date-format (new SimpleDateFormat "yyyy-MM-dd HH:mm:ss.SSS"))
(defn format-date [date]
  (.format standard-date-format date))

(defn watcher
  ([reference] (watcher reference {}))
  ([reference {:keys [limit elide-duplicates?]
               :or {limit 0
                    elide-duplicates? false}}]
   (let [{table :component} (data-table [{:timestamp (format-date (new Date)) :state @reference}])
         watch-key (str (UUID/randomUUID))
         channel-for-states (async/chan 100000)]
     (add-watch reference
                watch-key
                (fn [_ _ old-state new-state]
                  (when-not (and elide-duplicates? (= old-state new-state))
                    (async/put! channel-for-states {:timestamp (new Date) :state new-state}))))
     (let [fut (future
                 (loop []
                   (when-let [{:keys [timestamp state]} (async/<!! channel-for-states)]
                     (let [row-count (st/row-count table)]
                       (st/insert-at! table row-count {:timestamp (format-date timestamp) :state state})
                       (when (and (pos? limit) (>= row-count limit))
                         (dotimes [i (inc (- row-count limit))]
                           (st/remove-at! table i)))
                       (sc/scroll! table :to :bottom))
                     (recur))))]
       {:component table
        :on-close (fn [_]
                    (async/close! channel-for-states)
                    (future-cancel fut)
                    (remove-watch reference watch-key))}))))

(defn show-data-table!
  ([data] (show-data-table! data {} {}))
  ([data table-opts] (show-data-table! data table-opts {}))
  ([data table-opts frame-opts]
   (show! (merge (data-table data table-opts) frame-opts))))

(defn show-watcher!
  ([reference] (show-watcher! reference {} {}))
  ([reference watcher-opts] (show-watcher! reference watcher-opts {}))
  ([reference watcher-opts frame-opts]
   (show! (merge (watcher reference watcher-opts) frame-opts))))

(defn show-bar-chart! [& label-coll-pairs]
  (let [series (into {} (map
                          (fn [[label coll]]
                            ; TODO if coll [1 2 3] then do below
                            ; TODO if coll [[1 2] [1 2] [3 4]] then use first as x second as y
                            [label (xc/extract-series {:x first :y second} (map-indexed vector coll))])
                          (partition 2 label-coll-pairs)))]
    (xc/view (xc/category-chart series))))

(comment


  (show-bar-chart! "series1" [1 2 5 3 4 7 9 -1]
                   "series2" [5 3 7 9 1 20 5])

  (xc/view
    (xc/xy-chart
      {"Maxime" {:x (range 10)
                 :y (mapv #(+ % (* 3 (rand)))
                          (range 10))}
       "Tyrone" {:x (range 10)
                 :y (mapv #(+ 2 % (* 4 (rand)))
                          (range 0 5 0.5))}}
      {:title "Longest running distance"
       :x-axis {:title "Months (since start)"}
       :y-axis {:title "Distance"
                :decimal-pattern "##.## km"}
       :theme :matlab}))

  (show! (data-table [[1 "jon" 29]
                      [2 "jon2" {:test "nested" :hmmm [1 2 3]}]
                      [3 "jon3" 999]
                      [3 "jon3" 999 888]]
                     {:column-labels ["id" "name" "something" "optional"]}))

  (show! (data-table [[1 "jon" 29]
                      [2 "jon2" {:test "nested" :hmmm [1 2 3]}]
                      [3 "jon3" 999]
                      {:jello "furled"}
                      [3 "jon3" 999 888]]))

  (do
    (def x (atom 0))
    (show! (watcher x {:limit 0 :elide-duplicates? true}))
    (swap! x inc)
    (dotimes [i 100]
      (future
        (dotimes [j 100]
          (swap! x inc)))))

  (show! (data-table [(new Date) (new Date) (new Date)]))

  (show! (data-table (new Date)))

  (show! (data-table #{1 2 3}))

  (show! (data-table (bean (new Date))))

  (show! (data-table [1 2 3 4 5 6 7 8]))


  (show! (data-table [{:id 1 :name "jon" :age 29}
                      {:id 1 :name "jon2" :age 103}
                      {:id 1 :name "jon3" :age 999}]))


  (show! (data-table [{:id 1 :name "jon" :age 29 :meh "hi"}
                      {:id 1 :name "jon2" :age 103 :hi "meh"}
                      {:id 1 :name "jon3" :age 999}]))

  )
