# Read/Eval/Visualise/Loop

Simple tools for visualising data at the repl

```clojure
[joncampbelldev/revl "2.0.3"]
```

## Data-table

```clojure
(data-table data {:keys [override-column-labels]})
=> {:compoment javax.swing.JTable}
```

Table will adjust layout as sensibly as possible for sequences, sets and maps (homogeneous and heterogeneous) as well as other objects.
* `override-column-labels` to override inferred column headings

**Clicking** on a cell in a `data-table` will show a new `data-table` for the data within the cell.

## Watcher

```clojure
(watcher reference {:keys [limit elide-duplicates?]
                     :or {limit 0 elide-duplicates? false})
=> {:compoment javax.swing.JTable :on-close fn-to-cleanup-watch}
```
                     
Watches for changes to the reference and updates a `data-table` for each change with a timestamp and the new state.
* `limit` restricts the length of history kept, 0 means unlimited history
* `elide-duplicates?` will not record changes that maintain the same state as before

## Show it on-screen

```clojure
(show! {:keys [component on-close]})
```
Pops up a small scrollable window containing the swing component passed in.


## Example usage

```clojure
; watching changes on an atom
(def x (atom [1]))
(show! (watcher x {:limit 10 :elide-duplicates? true}))
(swap! x conj 2)
(swap! x identity)
(swap! x conj 3)


; showing various pieces of data
(show! (data-table [1 2 3]))

(show! (data-table [[1 "jon" :user]
                    [56 "dave" :admin]
                    [24 "roger" :user :optional-value]]
                    {:override-column-labels ["id" "name" "role" "other"]}))
                    
(show! (data-table [{:id 1 :name "jon" :role :user}
                    {:id 56 :name "dave" :role :admin}
                    {:id 24 :name "roger" :role :user :optional-key :optional-value}]))
                    
(show! (data-table {:a 1 :b 2 :c {:hello [1 2 3] :test "world" :other #{2 4}}))

(show! (data-table (new java.util.Date)))
```