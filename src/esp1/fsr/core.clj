(ns esp1.fsr.core
  "Core functions used internally by fsr.
   Can also be used to construct other mechanisms
   that take advantage of fsr's namespace metadata and route resolution functionality."
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [esp1.fsr.cache :as cache]))

(defn clear-route-cache!
  "Clear the route cache - useful for development mode with hot reloading.
   Delegates to the cache module.
   Returns the number of entries cleared."
  {:malli/schema [:=> [:catn] :int]}
  []
  (cache/clear!))

(defn clojure-file-ext
  "Returns the extension of the given file or filename if it is .clj or .cljc.
   Otherwise returns nil."
  {:malli/schema [:=> [:catn
                       [:file-or-filename [:or :file :file-name]]]
                  [:maybe [:string {:title "File extension"}]]]}
  [file-or-filename]
  (let [filename (if (instance? java.io.File file-or-filename)
                   (.getName file-or-filename)
                   file-or-filename)]
    (re-find #"\.cljc?$" filename)))

(defn file-ns-name-components
  "If this file is part of a clojure namespace,
   returns a vector of the namespace name components associated with this file.
   If this file is not part of a clojure namespace, returns nil."
  {:malli/schema [:=> [:catn
                       [:f :file]]
                  [:maybe [:sequential [:string {:title "Namespace name component"}]]]]}
  [f]
  (let [[clj-file] (->> (file-seq f)
                        (filter clojure-file-ext))]
    (when clj-file
      (let [[_ns ns-name] (edn/read (java.io.PushbackReader. (io/reader clj-file)))]
        (->> (str/split (str ns-name) #"\.")
             (drop-last (- (.getNameCount (.toPath clj-file))
                           (.getNameCount (.toPath f)))))))))

(defn get-root-ns-prefix
  "Given a root filesystem path,
   returns the associated root namespace prefix string.
   It does this by scanning the root fileystem path for a clojure file,
   and returning the portion of the namepace corresponding to the filesystem path."
  {:malli/schema [:=> [:catn
                       [:root-fs-path [:dir-path {:title "Root filesystem path"}]]]
                  [:string {:title "Root namespace prefix"}]]}
  [root-fs-path]
  (str/join "." (file-ns-name-components (io/file root-fs-path))))

(defn- filename-match-info
  "Given a filename,
   returns a vector 2-tuple containing a regex string for matching against this filename in a regex pattern,
   and a (possibly empty) vector of path parameter names.

   Parameters in filenames are delineated by single angle brackets `<` `>`
   to match a parameter value that does not contain a slash `/` character,
   or double angle brackets `<<` `>>`
   to match a parameter value that may contain a slash `/` character."
  {:malli/schema [:=> [:catn
                       [:filename :file-name]]
                  [:cat
                   [:string {:title "Regex pattern"}]
                   [:vector [:string {:title "Parameter name"}]]]]}
  [filename]
  (let [matcher (re-matcher #"<([^<>]+)>" filename)
        param-names (loop [[_ param-name] (re-find matcher)
                           param-names []]
                      (if param-name
                        (recur (re-find matcher) (conj param-names param-name))
                        param-names))]
    [(str "^"
          (-> filename
              (str/replace #"<<([^<>]+)>>" "(.*)")
              (str/replace #"<([^<>]+)>" "([^/]*)"))
          "(/(.*))?$")
     param-names]))

(defn- step-match
  "Tries to match the beginning of the given URI against the filenames in the given current working directory.
   If a match is found, the matched file is returned, along with a map of any matched path parameters, and the remaining unmatched portion of the URI.
   If no match is found, returns nil.
   If multiple matches are found, throws an error."
  {:malli/schema [:=> [:catn
                       [:uri [:string {:title "URI"}]]
                       [:cwd [:file {:title "CWD"}]]]
                  [:maybe [:cat
                           [:maybe :string {:title "Remaining URI"}]
                           [:file {:title "Matched file"}]
                           [:map-of {:title "Path parameters"} :string :string]]]]}
  [uri cwd]
  (let [match-infos
        (->> (.listFiles cwd)
             (map (fn [f]
                    (let [match-str (or (last (file-ns-name-components f))
                                        (let [match-str (.getName f)]
                                          ;; if this file is a clojure file, strip the extension
                                          (if-let [ext (clojure-file-ext match-str)]
                                            (subs match-str 0 (- (count match-str) (count ext)))
                                            match-str)))
                          [match-pattern param-names] (filename-match-info match-str)
                          [_ & matches] (re-matches (re-pattern match-pattern) uri)]
                      (when matches
                        (let [[param-values [_ remaining-uri]] (split-at (count param-names) matches)]
                          [remaining-uri
                           f
                           (zipmap param-names param-values)])))))
             (keep identity))]
    (cond
      (empty? match-infos) nil
      (= 1 (count match-infos)) (first match-infos)
      (> (count match-infos) 1) (let [[file-match-infos dir-match-infos]
                                      (partition-by (fn [[_ f _]]
                                                      (.isFile f))
                                                    match-infos)]
                                  (if (and
                                       ;; if there is only one file match
                                       (= 1 (count file-match-infos))
                                       ;; and none of the directory matches contain an index.clj or index.cljc
                                       (empty? (filter (fn [[_ f _]]
                                                         (or (.exists (io/file f "index.clj"))
                                                             (.exists (io/file f "index.cljc"))))
                                                       dir-match-infos)))

                                    ;; then return the file match-info
                                    (first file-match-infos)

                                    ;; otherwise throw an exception
                                    (throw (Exception. (str "URI '" uri "' has multiple matches in " (.getPath cwd) ": " (pr-str match-infos)))))))))

(defn- uri->file+params-uncached
  "Internal uncached version of route resolution.
   Resolves the given URI to a filesystem file or dir in the given current working directory.
   If a matching filesystem file or dir is found, returns a vector 2-tuple
   of the matched file/dir, and a map of matched path parameters.
   If no matching filesystem file or dir is found, returns nil."
  {:malli/schema [:=> [:catn
                       [:uri [:string {:title "URI"}]]
                       [:cwd [:file {:title "CWD"}]]]
                  [:maybe [:cat
                           [:file {:title "Matched file"}]
                           [:map-of {:title "Path parameters"} :string :string]]]]}
  [uri cwd]
  ;; Handle root path (empty URI) - check for index.clj/index.cljc directly in cwd
  (if (= uri "")
    (let [index-clj (io/file cwd "index.clj")
          index-cljc (io/file cwd "index.cljc")]
      (cond
        (.exists index-clj) [index-clj {}]
        (.exists index-cljc) [index-cljc {}]
        :else nil))
    ;; Normal path resolution
    (loop [uri uri
           cwd cwd
           params {}]
      (when-let [[remaining-uri matched-file matched-params] (step-match uri cwd)]
        (let [new-params (merge params matched-params)]
          (if (and remaining-uri (.isDirectory matched-file))
            (recur remaining-uri matched-file new-params)
            [matched-file new-params]))))))

(defn uri->file+params
  "Resolves the given URI to a filesystem file or dir in the given current working directory.
   If a matching filesystem file or dir is found, returns a vector 2-tuple
   of the matched file/dir, and a map of matched path parameters.
   If no matching filesystem file or dir is found, returns nil.

   URIs are normalized by stripping leading and trailing slashes before resolution.
   This allows the function to accept URIs in their natural form (e.g., from Ring requests)
   which typically include leading slashes (e.g., \"/foo/bar\" or \"foo/bar\" both work)."
  {:malli/schema [:=> [:catn
                       [:uri [:string {:title "URI"}]]
                       [:cwd [:file {:title "CWD"}]]]
                  [:maybe [:cat
                           [:file {:title "Matched file"}]
                           [:map-of {:title "Path parameters"} :string :string]]]]}
  [uri cwd]
  (let [normalized-uri (-> uri
                           (str/replace #"^/" "")   ; strip leading /
                           (str/replace #"/$" ""))] ; strip trailing /
    (if-let [cache-entry (cache/cache-get normalized-uri (.getPath cwd))]
      [(io/file (:resolved-path cache-entry)) (:params cache-entry)]
      (let [result (uri->file+params-uncached normalized-uri cwd)]
        (when result
          (let [[file params] result]
            (cache/put! normalized-uri (.getPath cwd) (.getPath file) params)))
        result))))

(defn file->clj
  "Given a file that is part of a clojure namespace,
   returns the File for the .clj or .cljc file that defines that namespace.
   Otherwise returns nil."
  {:malli/schema [:=> [:catn
                       [:f :file]]
                  [:maybe :file]]}
  [f]
  (or ;; if f is already a clojure file, return it
      (when (clojure-file-ext f)
        f)
      ;; if f is a directory, look for index.clj or index.cljc
      (when (.isDirectory f)
        (let [index-clj (io/file f "index.clj")
              index-cljc (io/file f "index.cljc")]
          (or (when (.exists index-clj) index-clj)
              (when (.exists index-cljc) index-cljc)
              ;; fallback to first clj file found
              (->> (file-seq f)
                   (filter clojure-file-ext)
                   first))))
      ;; otherwise scan for clj files
      (->> (file-seq f)
           (filter clojure-file-ext)
           first)))

(defn clj->ns-sym
  "Returns the namespace name symbol for the clojure file."
  {:malli/schema [:=> [:catn
                       [:maybe-clj-file [:maybe [:file {:title "Clojure file"}]]]]
                  [:maybe [:symbol {:title "Namespace symbol"}]]]}
  [maybe-clj-file]
  (when maybe-clj-file
    (when-let [clj-file (file->clj maybe-clj-file)]
      (let [[_ns ns-name] (edn/read (java.io.PushbackReader. (io/reader clj-file)))]
        ns-name))))

(defn ns-sym->uri
  "Returns the URI associated with the namespace symbol.
   If a namespace prefix is provided, it will be stripped from the URI."
  {:malli/schema [:=> [:catn
                       [:maybe-ns-sym [:maybe [:symbol {:title "Namespace symbol"}]]]
                       [:root-ns-prefix {:optional true} [:maybe [:string {:title "Root namespace prefix"}]]]]
                  [:maybe [:string {:title "URI"}]]]}
  ([maybe-ns-sym]
   (ns-sym->uri maybe-ns-sym nil))
  ([maybe-ns-sym root-ns-prefix]
   (when maybe-ns-sym
     (let [ns-without-prefix (if root-ns-prefix
                               (str/replace-first (str maybe-ns-sym)
                                                  (re-pattern (str "^" (java.util.regex.Pattern/quote root-ns-prefix) "\\."))
                                                  "")
                               (str maybe-ns-sym))
           ;; Strip .index suffix if present (represents index.clj files)
           ns-without-index (str/replace ns-without-prefix #"\.index$" "")
           ;; Handle case where only "index" remains after prefix stripping
           final-ns (if (= ns-without-index "index") "" ns-without-index)]
       (str (str/replace final-ns #"\." "/")))))) ; convert . to /

(defn ns-endpoint-meta
  "Returns the metadata map of the namespace symbol, enhanced with endpoint/ns key,
   along with any other metadata associated with the namespace."
  {:malli/schema [:=> [:catn
                       [:maybe-ns-sym [:maybe [:symbol {:title "Namespace symbol"}]]]]
                  :map]}
  [maybe-ns-sym]
  (when maybe-ns-sym
    (require maybe-ns-sym)
    (let [ns-meta (meta (find-ns maybe-ns-sym))]
      (assoc ns-meta :endpoint/ns maybe-ns-sym))))

(defn resolve-sym
  "Requires and resolves the symbol to a var.
   If the given symbol is not namespaced, the default namespace is used to resolve the symbol."
  {:malli/schema [:=> [:catn
                       [:s :symbol]
                       [:default-ns [:maybe :symbol]]]
                  [:maybe :any]]}
  [s default-ns]
  (if (namespace s)
    (do (require (symbol (namespace s)))
        (resolve s))
    (do (when default-ns
          (require default-ns))
        (ns-resolve default-ns s))))

(defn http-endpoint-fn
  "Returns the handler var for the given HTTP method and endpoint metadata.
   If no function is found in :endpoint/http, recursively delegates to :endpoint/type.
   If no function is found, returns nil."
  {:malli/schema [:=> [:catn
                       [:method [:keyword {:title "HTTP method"}]]
                       [:endpoint-meta :map]]
                  [:maybe [:fn #(var? %)]]]}
  [method endpoint-meta]
  (or
   ;; First try to find handler in :endpoint/http
   (when-let [http-meta (:endpoint/http endpoint-meta)]
     (when-let [fn-sym (get http-meta method)]
       (let [endpoint-ns (:endpoint/ns endpoint-meta)]
         (resolve-sym fn-sym endpoint-ns))))
   ;; If not found, try delegating to :endpoint/type
   (when-let [type-ns (:endpoint/type endpoint-meta)]
     (let [delegated-meta (ns-endpoint-meta type-ns)]
       (http-endpoint-fn method delegated-meta)))))
