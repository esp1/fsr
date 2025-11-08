(ns esp1.fsr.core-test
  (:require
   [clojure.java.io :as io]
   [clojure.string :as str]
   [clojure.test :refer [deftest is testing use-fixtures]]
   [clojure.test.check.generators :as gen]
   [esp1.fsr.core :refer [clojure-file-ext file->clj ns-sym->uri uri->file+params]]
   [esp1.fsr.schema :as fsr-schema]
   [malli.core :as m]
   [malli.dev :as mdev]
   [malli.generator :as mg]
   [malli.registry :as mr]))

(use-fixtures :once
  (fn [f]
    (mr/set-default-registry!
     (merge
      (m/comparator-schemas)
      (m/type-schemas)
      (m/sequence-schemas)
      (m/base-schemas)
      (fsr-schema/file-schemas)
      (fsr-schema/cache-schemas)
      (fsr-schema/route-schemas)))
    (mdev/start!)
    (f)
    (mdev/stop!)
    (mr/set-default-registry! m/default-registry)))

(deftest non-clj-file
  (is (= [(io/file "test/bar/test.txt") {}]
         (uri->file+params "bar/test.txt" (io/file "test"))))
  (is (= nil
         (file->clj (io/file "test/bar/test.txt")))))

(deftest clj-file
  (is (= [(io/file "test/bar/abc_<param1>_def_<<param2>>_xyz.clj") {"param1" "word", "param2" "m/n/o/p"}]
         (uri->file+params "abc-word-def-m/n/o/p-xyz" (io/file "test/bar"))))
  (is (= (io/file "test/bar/abc_<param1>_def_<<param2>>_xyz.clj")
         (file->clj (io/file "test/bar/abc_<param1>_def_<<param2>>_xyz.clj"))))

  (is (= [(io/file "test/bar/2024_01_02_Some_Thing") {}]
         (uri->file+params "bar/2024-01-02_Some_Thing" (io/file "test"))))
  (is (= (io/file "test/bar/2024_01_02_Some_Thing/index.clj")
         (file->clj (io/file "test/bar/2024_01_02_Some_Thing")))))

(deftest nonexistent-file
  (is (= nil
         (uri->file+params "nope/nuhuh" (io/file "not/here"))))
  (is (= nil
         (file->clj (io/file "not/here")))))

(deftest uri-slash-normalization
  (testing "uri->file+params normalizes leading slashes"
    (is (= [(io/file "test/bar/test.txt") {}]
           (uri->file+params "/bar/test.txt" (io/file "test")))
        "Leading slash should be stripped"))

  (testing "uri->file+params normalizes trailing slashes"
    (is (= [(io/file "test/bar/2024_01_02_Some_Thing") {}]
           (uri->file+params "bar/2024-01-02_Some_Thing/" (io/file "test")))
        "Trailing slash should be stripped"))

  (testing "uri->file+params normalizes both leading and trailing slashes"
    (is (= [(io/file "test/bar/2024_01_02_Some_Thing") {}]
           (uri->file+params "/bar/2024-01-02_Some_Thing/" (io/file "test")))
        "Both leading and trailing slashes should be stripped"))

  (testing "uri->file+params works with path parameters and leading slash"
    (is (= [(io/file "test/bar/abc_<param1>_def_<<param2>>_xyz.clj")
            {"param1" "word", "param2" "m/n/o/p"}]
           (uri->file+params "/abc-word-def-m/n/o/p-xyz" (io/file "test/bar")))
        "Leading slash should not interfere with path parameter matching")))

(deftest step-match
  (is (= [nil (io/file "test/baz/<<arg>>.clj") {"arg" "blah"}]
         (#'esp1.fsr.core/step-match "blah" (io/file "test/baz"))))
  (is (thrown? Exception
               (#'esp1.fsr.core/step-match "blah" (io/file "test/bad")))))

(deftest clojure-file-ext-test
  (testing "clojure-file-ext function"
    (let [args-schema (-> #'clojure-file-ext meta :malli/schema second)
          args-gen (mg/generator args-schema)]
      (dotimes [_ 100]
        (let [[filename] (gen/generate args-gen)
              result (clojure-file-ext filename)]
          (is (or (nil? result)
                  (re-matches #"\.cljc?$" result))))))))

(deftest filename-match-info-test
  (testing "filename-match-info produces valid regex patterns"
    (let [filename-gen (gen/one-of
                        [gen/string-alphanumeric
                         (gen/fmap #(str "prefix_<param>_" %)
                                   gen/string-alphanumeric)
                         (gen/fmap #(str "<<multi-param>>_" %)
                                   gen/string-alphanumeric)
                         (gen/fmap #(str % "_<a>_<b>_suffix")
                                   gen/string-alphanumeric)])]
      (dotimes [_ 100]
        (let [filename (gen/generate filename-gen)
              [pattern param-names] (#'esp1.fsr.core/filename-match-info filename)]
          ;; Property 1: Pattern should be a valid regex
          (is (re-pattern pattern))

          ;; Property 2: param-names should be a vector
          (is (vector? param-names))

          ;; Property 3: All param names should be strings
          (is (every? string? param-names))

          ;; Property 4: Pattern should start with ^ and contain trailing group
          (is (str/starts-with? pattern "^"))
          (is (str/includes? pattern "(/(.*))?$"))

          ;; Property 5: Number of capturing groups should match param count + 2 (for trailing groups)
          (let [capture-group-count (count (re-seq #"(?<!\\)\(" pattern))]
            (is (= capture-group-count (+ (count param-names) 2))))))))

  (testing "filename-match-info parameter extraction"
    ;; Test specific cases with known parameters
    ;; Note: filenames use underscores, URIs use dashes (converted elsewhere)
    (let [[pattern params] (#'esp1.fsr.core/filename-match-info "foo_<id>_bar")]
      (is (= params ["id"]))
      (is (re-matches (re-pattern pattern) "foo_123_bar")))

    (let [[pattern params] (#'esp1.fsr.core/filename-match-info "<<path>>_end")]
      (is (= params ["path"]))
      (is (re-matches (re-pattern pattern) "a/b/c_end")))

    (let [[pattern params] (#'esp1.fsr.core/filename-match-info "start_<a>_middle_<b>_end")]
      (is (= params ["a" "b"]))
      (is (re-matches (re-pattern pattern) "start_x_middle_y_end")))))

(deftest ns-sym->uri-test
  (testing "ns-sym->uri conversion properties"
    (let [ns-gen (gen/fmap #(symbol (str/join "." %))
                           (gen/vector gen/string-alphanumeric 1 5))
          prefix-gen gen/string-alphanumeric]
      (dotimes [_ 100]
        (let [ns-sym (gen/generate ns-gen)
              prefix (gen/generate prefix-gen)
              result (ns-sym->uri ns-sym prefix)]

          ;; Property 1: Result should be a string or nil
          (is (or (nil? result) (string? result)))

          ;; Property 2: Result should not contain dots (converted to /)
          (when result
            (is (not (str/includes? result "."))))

          ;; Property 3: Result may start with / if namespace doesn't match prefix
          ;; This happens when prefix doesn't match - the leading part becomes /something
          ;; We can't assert it never starts with / because that depends on prefix matching
          ))))

  (testing "ns-sym->uri specific conversions"
    ;; Test prefix stripping
    (is (= "bar/baz" (ns-sym->uri 'foo.bar.baz "foo")))

    ;; Test index stripping - strips .index before converting to /
    ;; foo.bar.index -> strip prefix -> bar.index -> strip .index -> bar
    (is (= "bar" (ns-sym->uri 'foo.bar.index "foo")))
    (is (= "" (ns-sym->uri 'foo.index "foo")))

    ;; Test dot to slash conversion
    (is (= "a/b/c" (ns-sym->uri 'a.b.c "")))

    ;; Test nil handling
    (is (nil? (ns-sym->uri nil "foo")))))

(deftest ns-sym->uri-roundtrip-test
  (testing "URI conversions preserve structure"
    (let [test-cases [['foo.bar.baz "foo" "bar/baz"]
                      ['a.b.c.index "a" "b/c"]
                      ['x.y.index "x" "y"]
                      ['single "" "single"]]]
      (doseq [[ns-sym prefix expected-uri] test-cases]
        (let [result (ns-sym->uri ns-sym prefix)]
          (is (= expected-uri result))

          ;; Property: Converting back should preserve namespace structure
          ;; (modulo prefix and index conventions)
          (when (and result (seq prefix))
            (let [uri-as-ns (str/replace result "/" ".")
                  reconstructed-ns (symbol (str prefix "." uri-as-ns))]
              (is (or (= ns-sym reconstructed-ns)
                      ;; or it's the index version
                      (= ns-sym (symbol (str prefix "." uri-as-ns ".index"))))))))))))
;; T101: Test file->clj prioritizes index.clj in directories
(deftest file->clj-prioritizes-index-test
  (testing "file->clj returns index.clj when directory has multiple .clj files"
    ;; The test/bar/2024_01_02_Some_Thing directory has both index.clj and other files
    ;; file->clj should prioritize index.clj over other .clj files
    (let [dir (io/file "test/bar/2024_01_02_Some_Thing")
          result (file->clj dir)]
      (is (= (io/file "test/bar/2024_01_02_Some_Thing/index.clj") result)
          "Should return index.clj when directory contains it")))

  (testing "file->clj handles directory without index.clj"
    ;; If a directory doesn't have index.clj, should fall back to first .clj file
    (let [temp-dir (io/file "test/temp-test-dir")
          temp-file (io/file temp-dir "other.clj")]
      (try
        (.mkdirs temp-dir)
        (spit temp-file "(ns test.temp)")
        (let [result (file->clj temp-dir)]
          (is (= temp-file result)
              "Should fall back to first .clj file when no index.clj"))
        (finally
          (.delete temp-file)
          (.delete temp-dir)))))

  (testing "file->clj returns clj file when given clj file directly"
    (let [clj-file (io/file "test/foo/index.clj")
          result (file->clj clj-file)]
      (is (= clj-file result)
          "Should return the file itself when given a .clj file"))))
