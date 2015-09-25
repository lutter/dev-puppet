(ns pe-bench
  (:require [puppetlabs.http.client.sync :as http]
            [clojure.java.io :as io]
            [cheshire.core :as json]
            [clj-time.core :as time]
            [pe-config]))

(def config pe-config/config)

(defn- add-ssl-config
  [params]
  (if (not (:ssl-ca-cert params))
    (merge params (:ssl-config config))
    params))

(defn- make-request
  ([method url] (make-request method url {}))
  ([method url params]
   (try
     (let [request (assoc (add-ssl-config params)
                          :url url
                          :method method
                          :headers {"Content-Type" "application/json"
                                    "Accept"       "application/json, text/pson;q=0.5"})
           {:keys [status body headers]} (http/request request)
           get-message #(or (:message %) (:msg %) "No message")]
       (cond
         (= status 200)
         (-> body
             (io/reader)
             (json/parse-stream true))
         (= status 204) {}
         :else
         (do
           (println (format "%s %s: received bad status %d" method url status))
           (if (re-find #"[jp]son" (or (headers "content-type") ""))
             (println
              (-> (io/reader body)
                  (json/parse-stream true)
                  (get-message))
              (slurp body))
             (println (slurp body)))
           {})))
     (catch Exception e
       (println (format "%s %s: exception thrown" method url))
       e))))

(defn get-request
  "Make an HTTP GET request to URL using the parameters in PARAMS and parse
  the JSON response.

  Return an empty map on anything but a 200 response"
  ([url]
   (make-request :get url {}))
  ([url params]
   (make-request :get url
                 {:query-params
                  (reduce-kv #(assoc %1 (name %2) %3) {} params)})))

(defn post-request
  "Make an HTTP POST request to URL sending the BODY. If BODY is not not a
  string, we try and serialize it as JSON"
  [url body]
  (make-request :post url {:body (if (string? body)
                                   body
                                   (json/generate-string body))}))

;; Mapping between sexpr and nested JSON arrays
(defn sexpr->arrays
  "Turn an sexpr into the equivalent JSON representation using nested arrays"
  [sexpr]
  (let [primitive->string
        (fn [primitive]
          (cond
            (or (symbol? primitive)
                (keyword? primitive))  (name primitive)
            ;; the NC doesn't like numbers in its queries
            (number? primitive)        (str primitive)
            :else                      primitive))]
    (if (sequential? sexpr)
      (let [exprs (map sexpr->arrays (rest sexpr))]
        (into [(primitive->string (first sexpr))] exprs))
      (primitive->string sexpr))))

(defn arrays->sexpr
  "Turn nested JSON arrays into an sexpr"
  [arrays]
  (letfn [(primitive->string [primitive]
            (if (string? primitive)
              (symbol primitive)
              primitive))]
    (if (sequential? arrays)
      (map arrays->sexpr arrays)
      (primitive->string arrays))))

;; Classifier functions
(defn nc-url
  [path]
  (str (:classifier-service config) path))

(defn nc-groups
  "Retrieve all the groups from the NC. If given an ID, retrieve only the
  group with that ID"
  ([] (get-request (nc-url "/groups")))
  ([id] (get-request (nc-url (format "/groups/%s" id)))))

(defn nc-delete-group
  "Delete the group with the given ID"
  [id]
  (make-request :delete (nc-url (format "/groups/%s" id))))

(defn nc-create-group
  "Create a classifier group. GROUP must be a map"
  [group]
  (let [group (merge {:classes {}} group)]
    (if-let [id (:id group)]
      (make-request :put (nc-url (format "/groups/%s" id))
                    {:body (dissoc group :id)})
      (post-request (nc-url "/groups") group))))

(defn nc-translate
  "Send the SEXPR to the classifier to have it turn it into a PuppetDB query"
  [sexpr]
  (arrays->sexpr (post-request (nc-url "/rules/translate")
                               (sexpr->arrays sexpr))))

;; PuppetDB functions
(defn pdb-url
  [path]
  (str (:puppetdb-service config) path))

(defn pdb-query
  [sexpr]
  (json/generate-string (sexpr->arrays sexpr)))

(defn pdb-nodes
  "Query PuppetDB for a list of nodes using the given SEXPR"
  ([] (get-request (pdb-url "/nodes")))
  ([sexpr]
   (get-request
    (pdb-url "/nodes")
    {:query (pdb-query sexpr)})))

(defn pdb-node-facts
  "Retrieve the facts for the given NODE"
  [node]
  (get-request (pdb-url (format "/nodes/%s/facts" node))))

(defn pdb-facts
  "QUery PuppetDB for facts using the given SEXPR"
  [sexpr]
  (get-request (pdb-url "/facts") {:query (pdb-query sexpr)}))

(defn pdb-command
  [name version payload]
  (post-request
   (str (:puppetdb-cmd-service config))
   {:command name
    :version version
    :payload payload}))

(defn pdb-replace-facts
  "Given a sequence of facts, which all have to have CLIENTCERT set, issue
  a 'replace facts' command to PuppetDB"
  [environment facts]
  (letfn [(to-wire [f]
            {:environment environment
             :certname (:clientcert f)
             :producer_timestamp (str (clj-time.coerce/to-date-time (time/now)))
             :values f})]
    (map #(pdb-command "replace facts" 4 (to-wire %)) facts)))

;; Create facts
(def default-facts-json
  "The JSON representation of our default facts. This is greatly cut down
  from the actual facts that facter sends"
  "{
  \"architecture\": \"x86_64\",
  \"blockdevices\": \"sr0,vda\",
  \"domain\": \"example.com\",
  \"facterversion\": \"3.0.2\",
  \"filesystems\": \"xfs\",
  \"fqdn\": \"localhost\",
  \"gid\": \"root\",
  \"hostname\": \"fake-default\",
  \"id\": \"root\",
  \"interfaces\": \"eth0,lo\",
  \"ipaddress\": \"192.168.122.118\",
  \"ipaddress_eth0\": \"192.168.122.118\",
  \"ipaddress_lo\": \"127.0.0.1\",
  \"is_virtual\": true,
  \"kernel\": \"Linux\",
  \"kernelmajversion\": \"3.10\",
  \"macaddress\": \"5e:54:00:ab:f1:8b\",
  \"macaddress_eth0\": \"5e:54:00:ab:f1:8b\",
  \"operatingsystem\": \"CentOS\",
  \"operatingsystemmajrelease\": \"7\",
  \"osfamily\": \"RedHat\",
  \"path\": \"/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin\",
  \"physicalprocessorcount\": 2,
  \"processor0\": \"Intel Core Processor (Broadwell)\",
  \"processor1\": \"Intel Core Processor (Broadwell)\",
  \"processorcount\": 2,
  \"rubyplatform\": \"x86_64-linux\",
  \"rubysitedir\": \"/opt/puppetlabs/puppet/lib/ruby/site_ruby/2.1.0\",
  \"rubyversion\": \"2.1.6\",
  \"timezone\": \"EDT\",
  \"uptime\": \"6 days\",
  \"uptime_days\": 6,
  \"uptime_hours\": 164,
  \"uptime_seconds\": 591328,
  \"uuid\": \"88186AC9-E6DF-6E49-8248-1ED9DE2C837C\",
  \"virtual\": \"kvm\"
}")

(def default-facts
  (json/parse-string default-facts-json true))

(defn fake-facts
  "Produce fake facts based. ID must be a number between 0 and 65536 and is
  used to generate a predictable hostname, UUID, and IP and MAC addresses"
  ([id] (fake-facts id "fake"))
  ([id hostname-prefix]
   (merge
    default-facts
    (let [n  (mod (quot id 256) 256)
          m  (mod id 256)
          ip (format "172.17.%d.%d" n m)
          hostname (format "%s-%d-%d" hostname-prefix n m)
          mac (format "5e:54:00:%02x:f1:%02x" n m)
          ncpus (+ 1 (rand-int 8))]
      (merge
       {:uuid (format "000000%02X-0000-4000-8000-0000000000%02X" n m)
        :ipaddress ip
        :ipaddress_eth0 ip
        :macaddress mac
        :macaddress_eth0 mac
        :operatingsystem (rand-nth '("CentOS" "Scientific" "RHEL"))
        :hostname hostname
        :domain "example.net"
        :clientcert (format "%s.%s" hostname "example.net")}
       (into {:physicalprocessorcount ncpus
              :processorcount ncpus}
             (map #(vector (keyword (format "processor%d" %)) "MOS 6502")
                  (range ncpus))))))))

(defn make-facts
  "Make facts for n hosts, randomly"
  [n hostname-prefix]
  (let [numbers (take n (shuffle (range 65536)))]
    (map #(fake-facts % hostname-prefix) numbers)))
