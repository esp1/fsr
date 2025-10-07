(ns esp1.fsr.cache
  "Route caching system for FSR with LRU eviction and performance metrics.

   Provides thread-safe caching of route resolutions to improve performance
   by avoiding repeated filesystem operations. Implements LRU (Least Recently Used)
   eviction policy to manage memory constraints.

   Usage:
     (require '[esp1.fsr.cache :as cache])

     ;; Add entry to cache
     (cache/put! \"/api/users/123\" \"/var/www\" \"/var/www/api/users/<id>.clj\" {\"id\" \"123\"})

     ;; Retrieve entry from cache
     (cache/get \"/api/users/123\" \"/var/www\")

     ;; Clear all entries
     (cache/clear!)

     ;; Invalidate by pattern
     (cache/invalidate! #\"/api/.*\")

     ;; Get performance metrics
     (cache/get-metrics)")

;; ============================================================================
;; T012: Cache State Atoms
;; ============================================================================

(def ^:private cache-store
  "Atom containing cache state with LRU tracking.
   Structure: {:entries {cache-key -> cache-entry}
               :access-order [cache-key ...] ; most recent last}"
  (atom {:entries {}
         :access-order []}))

(def ^:private cache-config
  "Atom containing cache configuration.
   Fields: :max-entries, :enabled?, :eviction-policy"
  (atom {:max-entries 1000
         :enabled? true
         :eviction-policy :lru}))

(def ^:private cache-metrics
  "Atom containing performance metrics.
   Fields: :hits, :misses, :evictions, :current-size"
  (atom {:hits 0
         :misses 0
         :evictions 0
         :current-size 0}))

;; ============================================================================
;; T013: Cache Data Structures
;; ============================================================================

(defn- make-cache-key
  "Create cache key from URI and root path."
  {:malli/schema [:=> [:cat :string :string] :string]}
  [uri root-path]
  (str uri "|" root-path))

(defn- make-cache-entry
  "Create cache entry with current timestamp."
  {:malli/schema [:=> [:cat :string :string :string :map] :map]}
  [uri root-path resolved-path params]
  {:uri uri
   :root-path root-path
   :resolved-path resolved-path
   :params params
   :timestamp (System/currentTimeMillis)})

(defn- update-access-order
  "Update LRU access order by moving key to end (most recent).
   Removes key from current position and appends to end."
  [access-order cache-key]
  (let [without-key (filterv #(not= % cache-key) access-order)]
    (conj without-key cache-key)))

;; ============================================================================
;; T014: Cache Operations with LRU Eviction
;; ============================================================================

(defn cache-get
  "Retrieve entry from cache, updating access time for LRU.
   Returns cache entry or nil if not found or cache disabled."
  {:malli/schema [:=> [:cat :string :string] [:maybe :map]]}
  [uri root-path]
  (when (:enabled? @cache-config)
    (let [cache-key (make-cache-key uri root-path)]
      (swap! cache-store
             (fn [store]
               (if-let [entry (get-in store [:entries cache-key])]
                 ;; Entry found - update access order and timestamp
                 (let [updated-entry (assoc entry :timestamp (System/currentTimeMillis))]
                   (-> store
                       (assoc-in [:entries cache-key] updated-entry)
                       (update :access-order update-access-order cache-key)))
                 ;; Entry not found - no change
                 store)))

      (let [entry (get-in @cache-store [:entries cache-key])]
        (if entry
          (do
            (swap! cache-metrics update :hits inc)
            entry)
          (do
            (swap! cache-metrics update :misses inc)
            nil))))))

(defn- evict-lru!
  "Evict least recently used entry from cache.
   Returns the evicted cache key."
  []
  (let [lru-key (first (:access-order @cache-store))]
    (when lru-key
      (swap! cache-store
             (fn [store]
               (-> store
                   (update :entries dissoc lru-key)
                   (update :access-order (fn [order] (vec (rest order)))))))
      (swap! cache-metrics update :evictions inc)
      (swap! cache-metrics update :current-size dec)
      lru-key)))

(defn put!
  "Add entry to cache with LRU eviction if needed.
   Updates existing entry if cache key already exists.
   Respects :enabled? config flag."
  {:malli/schema [:=> [:cat :string :string :string :map] [:maybe :map]]}
  [uri root-path resolved-path params]
  (when (:enabled? @cache-config)
    (let [cache-key (make-cache-key uri root-path)
          config @cache-config
          max-entries (:max-entries config)
          is-new-entry? (not (contains? (:entries @cache-store) cache-key))]

      ;; Check if we need to evict before adding
      (when (and is-new-entry?
                 (>= (count (:entries @cache-store)) max-entries))
        (evict-lru!))

      ;; Add or update entry
      (let [entry (make-cache-entry uri root-path resolved-path params)]
        (swap! cache-store
               (fn [store]
                 (cond-> store
                   true (assoc-in [:entries cache-key] entry)
                   true (update :access-order update-access-order cache-key))))

        ;; Update size metric if new entry
        (when is-new-entry?
          (swap! cache-metrics update :current-size inc))

        entry))))

;; ============================================================================
;; T015: Cache Management Functions
;; ============================================================================

(defn clear!
  "Clear all entries from cache.
   Returns the number of entries that were cleared."
  {:malli/schema [:=> [:cat] :int]}
  []
  (let [count-before (count (:entries @cache-store))]
    (reset! cache-store {:entries {} :access-order []})
    (swap! cache-metrics assoc :current-size 0)
    count-before))

(defn invalidate!
  "Invalidate cache entries matching regex pattern.
   Pattern is matched against the full cache key (uri|root-path).
   Returns the number of entries invalidated."
  {:malli/schema [:=> [:cat :any] :int]}
  [pattern]
  (let [regex-pattern (if (instance? java.util.regex.Pattern pattern)
                        pattern
                        (re-pattern pattern))
        entries-before (:entries @cache-store)
        keys-to-remove (filter #(re-find regex-pattern %) (keys entries-before))]

    (swap! cache-store
           (fn [store]
             (-> store
                 (update :entries #(apply dissoc % keys-to-remove))
                 (update :access-order (fn [order]
                                         (filterv (fn [k] (not (some #{k} keys-to-remove)))
                                                  order))))))

    (swap! cache-metrics update :current-size - (count keys-to-remove))
    (count keys-to-remove)))

(defn configure!
  "Update cache configuration at runtime.
   Accepts map with keys: :max-entries, :enabled?, :eviction-policy.
   Returns the updated configuration."
  {:malli/schema [:=> [:cat :map] :map]}
  [config-map]
  (swap! cache-config merge config-map)
  @cache-config)

;; ============================================================================
;; T016: Metrics Functions
;; ============================================================================

(defn get-metrics
  "Get current cache performance metrics.
   Returns map with :hits, :misses, :evictions, :current-size, and :hit-rate."
  {:malli/schema [:=> [:cat] :map]}
  []
  (let [metrics @cache-metrics
        hits (:hits metrics)
        misses (:misses metrics)
        total (+ hits misses)
        hit-rate (if (zero? total)
                   0.0
                   (double (/ hits total)))]
    (assoc metrics :hit-rate hit-rate)))

(defn debug-info
  "Get detailed cache state for debugging (development only).
   Returns map with :config, :metrics, :sample-keys, and :stats."
  {:malli/schema [:=> [:cat] :map]}
  []
  (let [store @cache-store
        entries (:entries store)
        sample-keys (take 10 (keys entries))]
    {:config @cache-config
     :metrics (get-metrics)
     :sample-keys sample-keys
     :stats {:total-entries (count entries)
             :access-order-length (count (:access-order store))}}))

;; Public API alias for convenience
(def get
  "Alias for cache-get. Retrieve entry from cache."
  cache-get)
