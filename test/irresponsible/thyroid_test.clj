(ns irresponsible.thyroid-test
  (:require [clojure.test :as t :refer [deftest testing is]]
            [clojure.spec.gen.alpha :as gen]
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
                                          :prefix "test-data"
                                          :suffix ".html"})]
        (is (instance? FileTemplateResolver r))
        (is (= "test-data" (.getPrefix r)))
        (is (= ".html" (.getSuffix r)))))

    (testing "with required params"
      (let [r (thyroid/template-resolver {:type :file
                                          :prefix "test-data"
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
  (testing "resolvers are required"
    (is (thrown? ExceptionInfo (thyroid/make-engine {})))))

(deftest test-render
  (let [resolver (thyroid/template-resolver {:type :string})
        engine (thyroid/make-engine {:resolvers [resolver]})]
    (testing "renders template to a string"
      (let [s (thyroid/render engine
                              "<h1 th:text=${title}></h1>"
                              {"title" "Hello World"})]
        (is (= "<h1>Hello World</h1>title"))))))
