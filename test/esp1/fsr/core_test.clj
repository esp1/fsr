(ns esp1.fsr.core-test
  (:require [clojure.java.io :as io]
            [clojure.test :refer [deftest is]]
            [esp1.fsr.core :refer [file->clj uri->file+params]]))

(deftest non-clj-file
  (is (= [(io/file "test/bar/test.txt") {}]
         (uri->file+params "bar/test.txt" (io/file "test"))))
  (is (= nil
         (file->clj (io/file "test/bar/test.txt")))))

(deftest clj-file
  (is (= [(io/file "test/bar/abc_<param1>_def_<<param2>>_xyz.clj") {:param1 "word", :param2 "m/n/o/p"}]
         (uri->file+params "abc-word-def-m/n/o/p-xyz" (io/file "test/bar"))))
  (is (= (io/file "test/bar/abc_<param1>_def_<<param2>>_xyz.clj")
         (file->clj (io/file "test/bar/abc_<param1>_def_<<param2>>_xyz.clj"))))
  
  (is (= [(io/file "test/bar/2024_01_02_Some_Thing") {}]
         (uri->file+params "bar/2024-01-02-Some-Thing" (io/file "test"))))
  (is (= (io/file "test/bar/2024_01_02_Some_Thing/index.clj")
         (file->clj (io/file "test/bar/2024_01_02_Some_Thing")))))

(deftest nonexistent-file
  (is (= nil
         (uri->file+params "nope/nuhuh" (io/file "not/here"))))
  (is (= nil
         (file->clj (io/file "not/here")))))

(deftest step-match
  (is (= [nil (io/file "test/baz/<<arg>>.clj") {:arg "blah"}]
         (#'esp1.fsr.core/step-match "blah" (io/file "test/baz"))))
  (is (thrown? Exception
               (#'esp1.fsr.core/step-match "blah" (io/file "test/bad")))))