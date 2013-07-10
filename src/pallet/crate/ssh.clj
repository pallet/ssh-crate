(ns pallet.crate.ssh
  "Crate for managing ssh"
  (:require
   [clj-schema.schema :refer [def-map-schema map-schema optional-path
                              sequence-of]]
   [clojure.tools.logging :refer [debugf]]
   [pallet.actions :refer [directory remote-file]]
   [pallet.api :as api :refer [plan-fn]]
   [pallet.config-file.format :refer [name-values]]
   [pallet.contracts :refer [check-spec]]
   [pallet.crate :refer [assoc-settings defplan get-settings]]
   [pallet.crate-install :as crate-install :refer [crate-install-settings]]
   [pallet.crate.service
    :refer [supervisor-config supervisor-config-map] :as service]
   [pallet.script.lib :refer [config-root exit file log-root pid-root]]
   [pallet.stevedore :refer [fragment script]]
   [pallet.utils :refer [apply-map deep-merge maybe-update-in]]
   [pallet.version-dispatch :refer [defmethod-version-plan
                                    defmulti-version-plan]]))


;;; # Settings
(def-map-schema ssh-settings-schema
  crate-install-settings
  [[:sshd-config] (map-schema :loose [])
   [:sshd-config-file] string?
   [:supervisor] keyword?
   [:run-command] string?
   [:service-name] string?
   [:service-log-dir] string?
   [:config-base] string?])

(defmacro check-ssh-settings
  [m]
  (check-spec m `ssh-settings-schema &form))

(defn defaults
  []
  {:sshd-config {"AcceptEnv" "LANG LC_*"
                 "ChallengeResponseAuthentication" "no"
                 "HostKey" ["/etc/ssh/ssh_host_dsa_key"
                            "/etc/ssh/ssh_host_ecdsa_key"
                            "/etc/ssh/ssh_host_rsa_key"]
                 "HostbasedAuthentication" "no"
                 "IgnoreRhosts" "yes"
                 "KeyRegenerationInterval" 3600
                 "LogLevel" "INFO"
                 "LoginGraceTime" 120
                 "PermitEmptyPasswords" "no"
                 "PermitRootLogin" "yes"
                 "Port" 22
                 "PrintLastLog" "yes"
                 "PrintMotd" "no"
                 "Protocol" 2
                 "PubkeyAuthentication" "yes"
                 "RSAAuthentication" "yes"
                 "RhostsRSAAuthentication" "no"
                 "ServerKeyBits" 768
                 "StrictModes" "yes"
                 "Subsystem" "sftp /usr/lib/openssh/sftp-server"
                 "SyslogFacility" "AUTH"
                 "TCPKeepAlive" "yes"
                 "UsePAM" "yes"
                 "UsePrivilegeSeparation" "yes"
                 "X11DisplayOffset" 10
                 "X11Forwarding" "yes"}
   :supervisor :initd
   :run-command "/usr/sbin/sshd -D"
   :service-name "sshd"
   :service-log-dir (fragment (file (log-root) "ssh"))
   :config-base (fragment (file (config-root) "ssh"))})

(defn derived-defaults
  [{:keys [config-base] :as settings}]
  (debugf "derived-defaults settings %s" settings)
  (->
   settings
   (update-in [:sshd-config-file]
              #(or % (fragment (file ~config-base "sshd_config"))))))

;;; ## Installation Settings

;;; At the moment we just have a single implementation of settings,
;;; but this is open-coded.
(defmulti-version-plan settings-map [version settings])

(defmethod-version-plan
    settings-map {:os :linux}
    [os os-version version settings]
  (cond
   (:install-strategy settings) settings
   :else (merge {:packages ["openssh"]
                 :service-name "sshd"}
                (assoc settings :install-strategy :packages))))

(defmethod-version-plan
    settings-map {:os :debian-base}
    [os os-version version settings]
  (cond
   (:install-strategy settings) settings
   :else (merge {:packages ["openssh-server"]
                 :service-name "ssh"}
                (assoc settings :install-strategy :packages))))


;;; ## Supervisor Settings
(defmethod supervisor-config-map [:ssh :initd]
  [_ {:keys [service-name] :as settings} options]
  {:service-name service-name})

(defmethod supervisor-config-map [:ssh :runit]
  [_ {:keys [run-command service-name service-log-dir] :as settings} options]
  {:service-name service-name
   :run-file {:content (str "#!/bin/sh\nexec 1>&2\nexec "
                            run-command " -e")}
   :log-run-file {:content (str "#!/bin/sh\nexec svlogd -tt "
                                service-log-dir)}})

(defmethod supervisor-config-map [:ssh :upstart]
  [_ {:keys [run-command service-name] :as settings} options]
  {:service-name service-name
   :exec run-command})

(defmethod supervisor-config-map [:ssh :nohup]
  [_ {:keys [run-command service-name] :as settings} options]
  {:service-name service-name
   :run-file {:content run-command}})

(defplan settings
  "Apply settings for ssh."
  [{:keys [conf-root run-rot spool-root main master] :as settings}
   & {:keys [instance-id] :as options}]
  (debugf "settings 0 %s" settings)
  (let [settings (deep-merge (defaults) settings)
        _ (debugf "settings 1 %s" settings)
        settings (derived-defaults
                  (settings-map (:version settings) settings))]
    (debugf "settings %s" settings)
    (check-ssh-settings settings)
    (assoc-settings :ssh settings options)
    (supervisor-config :ssh settings (or options {}))))


;;; # Install
(defplan install
  "Install OpenSSH."
  [{:keys [instance-id]}]
  (let [{:keys [supervisor service-log-dir] :as settings}
        (get-settings :ssh {:instance-id instance-id})]
    (crate-install/install :ssh instance-id)
    (when (= :runit supervisor)
      (directory service-log-dir))))

;;; # Configuration
(defn sshd-config-content
  "Return the content for sshd_config, given a config map.
  Vector values in config will be expanded to multiple entries in the config
  file."
  [config]
  (name-values (mapcat #(if (vector? (val %))
                          (mapv (fn [v] [(key %) v]) (val %))
                          [[(key %) (val %)]]) config)))

(defn write-sshd-config
  "Take an sshd config string, and write to sshd_conf."
  [config-file config]
  (remote-file
   config-file
   :mode "0644"
   :owner "root"
   :content config))

(defplan configure
  "Write ssh configuration."
  [{:keys [instance-id] :as options}]
  (let [{:keys [sshd-config sshd-config-file]} (get-settings :ssh options)]
    (write-sshd-config sshd-config-file (sshd-config-content sshd-config))))

;;; # Run
(defplan service
  "Run the ssh service."
  [& {:keys [action if-flag if-stopped instance-id]
      :or {action :manage}
      :as options}]
  (let [{:keys [supervision-options] :as settings}
        (get-settings :ssh {:instance-id instance-id})]
    (service/service settings (merge supervision-options
                                     (dissoc options :instance-id)))))


;;; # Server Spec
(defn server-spec
  [settings & {:keys [instance-id] :as options}]
  (api/server-spec
   :phases {:settings (plan-fn
                        (apply-map pallet.crate.ssh/settings
                                   settings options))
            :install (plan-fn (install options))
            :configure (plan-fn (configure options))}
   :default-phases [:install :configure]))

;; (defn iptables-accept
;;   "Accept ssh, by default on port 22"
;;   ([request] (iptables-accept request 22))
;;   ([request port]
;;      (iptables/iptables-accept-port request port)))

;; (defn iptables-throttle
;;   "Throttle ssh connection attempts, by default on port 22"
;;   ([request] (iptables-throttle request 22))
;;   ([request port] (iptables-throttle request port 60 6))
;;   ([request port time-period hitcount]
;;      (iptables/iptables-throttle
;;       request
;;       "SSH_CHECK" port "tcp" time-period hitcount)))

;; (defn nagios-monitor
;;   "Configure nagios monitoring for ssh"
;;   [request & {:keys [command] :as options}]
;;   (nagios-config/service
;;    request
;;    (merge
;;     {:servicegroups [:ssh-services]
;;      :service_description "SSH"
;;      :check_command "check_ssh"}
;;     options)))
