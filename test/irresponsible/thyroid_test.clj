(ns irresponsible.thyroid-test
  (:require [clojure.test :as t :refer [deftest testing is]]
            [irresponsible.domiscuity.dom :as dom]
            [irresponsible.domiscuity.parser :as dom-parser]
            [irresponsible.thyroid :as thyroid])
  (:import [clojure.lang ExceptionInfo]
           [org.thymeleaf.templateresolver
            FileTemplateResolver StringTemplateResolver]))

(deftest test-template-resolver
  (testing "file resolver"
    (testing "with missing required params"
      (is (thrown? ExceptionInfo (thyroid/template-resolver {:type :file}))))

    (testing "with required params"
      (let [r (thyroid/template-resolver {:type :file
                                          :prefix "test-data/"
                                          :suffix ".html"})]
        (is (instance? FileTemplateResolver r))
        (is (= "test-data/" (.getPrefix r)))
        (is (= ".html" (.getSuffix r)))))

    (testing "adds trailing slash to prefix path"
      (let [r (thyroid/template-resolver {:type :file
                                          :prefix "test-data"
                                          :suffix ".html"})]
        (is (= "test-data/" (.getPrefix r)))))

    (testing "with required params"
      (let [r (thyroid/template-resolver {:type :file
                                          :prefix "test-data/"
                                          :suffix ".html"
                                          :cache? true
                                          :cache-ttl 420})]
        (is (.isCacheable r))
        (is (= 420 (.getCacheTTLMs r))))))

  (testing "string resolver"
    (testing "with no params"
      (let [r (thyroid/template-resolver {:type :string})]
        (is (instance? StringTemplateResolver r))))

    (testing "with optional params"
      (let [r (thyroid/template-resolver {:type :string
                                          :cache? true
                                          :cache-ttl 420})]
        (is (.isCacheable r))
        (is (= 420 (.getCacheTTLMs r))))))

  (testing "throws when resolver type doesn't exist"
    (is (thrown? ExceptionInfo
                 (thyroid/template-resolver {:type :invalid})))))

(deftest test-dialect)

(deftest test-process-by-tag)

(deftest test-process-by-attrs)

(deftest test-munge-key
  (is (= "nogaps" (thyroid/munge-key :nogaps)))
  (is (= "a_simple_var" (thyroid/munge-key :a-simple-var)))
  (is (= "a_spaced_var" (thyroid/munge-key "a spaced   var")))
  (is (= "a_symbol" (thyroid/munge-key 'a-symbol)))
  (is (= "" (thyroid/munge-key ""))))

(deftest test-munge-coll
  (testing "converts maps"
    (is (= {"a" 1, "and_b" 2} (thyroid/munge-coll {:a 1, :and-b 2})))

    (testing "with nested map"
      (is (= {"a" {"nested_a" 1, "nested_b" 2}}
             (thyroid/munge-coll {:a {:nested-a 1, :nested-b 2}}))))

    (testing "with a list of maps"
      (is (= {"a" [{"nested_name" 1} {"nested_name" 2}]}
             (thyroid/munge-coll {:a [{:nested-name 1} {:nested-name 2}]}))))

    (testing "preserves collection type"
      (is (instance? clojure.lang.PersistentArrayMap
                     (thyroid/munge-coll {:a 1})))
      (is (instance? clojure.lang.PersistentTreeMap
                     (thyroid/munge-coll (sorted-map :a 1 :b 2))))))

  (testing "does nothing with normal lists"
    (is (= [:a 1, :and-b 2] (thyroid/munge-coll [:a 1, :and-b 2])))
    (is (= #{:a 1, :and-b 2} (thyroid/munge-coll #{:a 1, :and-b 2})))))

(deftest test-context
  (testing "creates a context using a variables map"
    (testing "with strings as keys"
      (let [c (thyroid/context {"title" "Hello World"})]
        (is (= (.getVariable c "title") "Hello World"))))

    (testing "with keywords as keys"
      (let [c (thyroid/context {:title "Hello World"})]
        (is (= (.getVariable c "title") "Hello World"))))

    (testing "with symbols as keys"
      (let [c (thyroid/context {'title "Hello World"})]
        (is (= (.getVariable c "title") "Hello World")))))

  (testing "munges clojure-style keys"
    (let [c (thyroid/context {:a-key-var "Testing..."})]
      (is (= (.getVariable c "a_key_var") "Testing..."))))

  (testing "does nothing with an empty map"
    (let [c (thyroid/context {})]
      (is (empty? (.getVariableNames c))))))

(deftest test-make-engine
  (testing "accepts multiple resolvers"
    (let [resolvers (map thyroid/template-resolver
                         [{:type :file, :prefix "test-data/", :suffix".html"}
                          {:type :string}])
          engine (thyroid/make-engine {:resolvers resolvers})]
      (is (= (set resolvers) (.getTemplateResolvers engine)))))

  (testing "optionally accepts dialects"
    (let [dialects [(thyroid/dialect {:name "dialect1", :prefix "d1", :handler identity})
                    (thyroid/dialect {:name "dialect2", :prefix "d2", :handler identity})]
          engine (thyroid/make-engine {:resolvers [(thyroid/template-resolver
                                                    {:type :string})]
                                       :dialects dialects})]
      (is (clojure.set/subset? dialects (.getDialects engine)))))

  (testing "resolvers are required"
    (is (thrown? ExceptionInfo (thyroid/make-engine {})))))

(deftest test-render
  (testing "with file resolver"
    (let [resolver (thyroid/template-resolver {:type :file
                                               :prefix "test-data/"
                                               :suffix ".html"
                                               :cache? false})
          engine (thyroid/make-engine {:resolvers [resolver]})]
      (testing "basic template"
        (let [html (dom-parser/doc
                    (thyroid/render engine "basic-template.html" {:title "Hello World"}))
              [h1-tag] (dom/find-by-tag html "h1")]
          (is (not (nil? h1-tag)))
          (is (= "Hello World" (dom/text h1-tag)))))

      (testing "iteration template"
        (let [html (dom-parser/doc
                    (thyroid/render engine "iteration.html" {:items [{:name "Ice Cream"
                                                                      :price 9.99}
                                                                     {:name "Coffee"
                                                                      :price 15.99}]}))
              items (dom/child-elems (dom/find-by-id html "items"))]
          (is (= 2 (count items)))
          (let [[ice-cream coffee] items]
            (is (= "Ice Cream - $9.99" (dom/text ice-cream)))
            (is (= "Coffee - $15.99" (dom/text coffee))))))))

  (testing "with string resolver"
    (let [resolver (thyroid/template-resolver {:type :string, :cache? false})
          engine (thyroid/make-engine {:resolvers [resolver]})]
      (testing "renders template to a string"
        (let [s (thyroid/render engine
                                "<h1 th:text=${title}></h1>"
                                {"title" "Hello World"})]
          (is (= s "<h1>Hello World</h1>"))))

      (testing "accepts clojure style variable names"
        (let [s (thyroid/render engine
                                "<h1 th:text=${clj_style_var}></h1>"
                                {:clj-style-var "Hello World"})]
          (is (= s "<h1>Hello World</h1>")))))))
