(defproject fschema "0.2.0-SNAPSHOT"
  :description "Elegant functional validations and mutators for Clojure and Clojurescript"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.6.0"]]
  :cljx {:builds [{:source-paths ["src"]
                 :output-path "target/classes"
                 :rules :clj}

                {:source-paths ["src"]
                 :output-path "target/classes"
                 :rules :cljs}]}
  :hooks [cljx.hooks]
  :profiles
  {:dev
   {:dependencies [[com.cemerick/piggieback "0.1.3"]
                   [criterium "0.4.3"]]
    :plugins [[com.keminglabs/cljx "0.4.0" :exclusions [org.clojure/clojure]]
    ;          [com.cemerick/austin "0.1.4"]
              ]
    :jvm-opts ^:replace []}})
