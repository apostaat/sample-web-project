(defproject sample-web-back "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "EPL-2.0 OR GPL-2.0-or-later WITH Classpath-exception-2.0"
            :url "https://www.eclipse.org/legal/epl-2.0/"}
  :dependencies [[org.clojure/clojure "1.10.0"]
                 [org.clojure/java.jdbc "0.7.11"]
                 [org.postgresql/postgresql "42.2.18.jre7"]
                 [prismatic/schema "1.1.12"]
                 [environ "1.2.0"]
                 [clj-time "0.15.2"]
                 [metosin/compojure-api "2.0.0-alpha30"]
                 [ring/ring-mock "0.3.2"]
                 [javax.servlet/javax.servlet-api "3.1.0"]
                 [ring/ring-core "1.8.0"]
                 [cheshire "5.10.0"]
                 [clj-http "3.10.0"]
                 [org.clojure/tools.logging "1.1.0"]]
  :plugins [[lein-ring "0.12.5"]
            [lein-environ "1.2.0"]]
  :profiles {:dev {:env {:dbname "sample-web-project-db"
                         :user "sample-web-project"
                         :password "sample-web-project"}}
             :uberjar {:aot :all}}
  :repl-options {:init-ns app.core}
  :ring {:handler app.core/handler
         :port 3030})
