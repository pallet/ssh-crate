(ns pallet.crate.ssh-test
  (:require
   [clojure.test :refer :all]
   [pallet.actions :refer [package-manager]]
   [pallet.api :refer [plan-fn server-spec]]
   [pallet.build-actions :as build-actions]
   ;; [pallet.crate.iptables :as iptables]
   [pallet.crate.initd :as initd]
   [pallet.crate.network-service :refer [wait-for-port-listen]]
   [pallet.crate.ssh :as ssh]
   [pallet.test-utils :as test-utils]))

;; (deftest iptables-accept-test
;;   []
;;   (is (= (first
;;           (build-actions/build-actions
;;            {:server {:group-name :n :image {:os-family :ubuntu}}}
;;            (iptables/iptables-accept-port 22 "tcp")))
;;          (first
;;           (build-actions/build-actions
;;            {:server {:group-name :n :image {:os-family :ubuntu}}}
;;            (iptables-accept))))))

(use-fixtures :once
  test-utils/with-bash-script-language
  test-utils/with-ubuntu-script-template)

(deftest sshd-config-test
  (is (=
       "LogLevel INFO
Port 22
PrintLastLog yes
ServerKeyBits 768
HostbasedAuthentication no
AcceptEnv LANG LC_*
PermitEmptyPasswords no
TCPKeepAlive yes
StrictModes yes
IgnoreRhosts yes
PermitRootLogin yes
Subsystem sftp /usr/lib/openssh/sftp-server
KeyRegenerationInterval 3600
UsePrivilegeSeparation yes
X11Forwarding yes
UsePAM yes
HostKey /etc/ssh/ssh_host_dsa_key
HostKey /etc/ssh/ssh_host_ecdsa_key
HostKey /etc/ssh/ssh_host_rsa_key
RSAAuthentication yes
Protocol 2
SyslogFacility AUTH
PubkeyAuthentication yes
ChallengeResponseAuthentication no
LoginGraceTime 120
X11DisplayOffset 10
RhostsRSAAuthentication no
PrintMotd no
"
       (ssh/sshd-config-content (:sshd-config (ssh/defaults))))))

(deftest invoke-test
  (is (build-actions/build-actions
       {:server {:node (test-utils/make-node "tag" :id "id")}}
       (ssh/settings {})
       (ssh/configure {})
       (ssh/install {})
       (ssh/service :action :start))))

(defn live-test-spec
  [settings & {:keys [instance-id] :as options}]
  (server-spec
   :extends [(ssh/server-spec {})]
   :phases {:install (plan-fn (package-manager :update))
            :test (plan-fn
                    (ssh/service :action :start)
                    (wait-for-port-listen 22))}
   :default-phases [:install :configure :test]))
