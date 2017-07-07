(ns irresponsible.thyroid-test
  (:require [clojure.test :as t :refer [deftest testing is]]
            [irresponsible.thyroid :as thyroid])
  (:import (clojure.lang ExceptionInfo)))

(deftest test-template-resolver)

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

(deftest test-resolvers
  (testing "file")
  (testing "string"))

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
