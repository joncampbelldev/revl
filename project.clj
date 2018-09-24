(defproject joncampbelldev/revl "2.0.3"
  :description "Read/Eval/Visualise/Loop: Simple tools for visualising data at the repl"
  :url "https://github.com/joncampbelldev/revl"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.9.0"]
                 [seesaw "1.5.0"]
                 [com.hypirion/clj-xchart "0.2.0"]]
  :target-path "target/%s"
  :profiles {:dev {:source-paths ["dev"]}})
