(defproject com.palletops/ssh-crate "0.8.0-SNAPSHOT"
  :description "Crate for ssh installation"
  :url "http://github.com/pallet/ssh-crate"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.4.0"]
                 [com.palletops/pallet "0.8.0-RC.1"]]
  :resource {:resource-paths ["doc-src"]
             :target-path "target/classes/pallet_crate/ssh_crate/"
             :includes [#"doc-src/USAGE.*"]}
  :prep-tasks ["resource" "crate-doc"])
