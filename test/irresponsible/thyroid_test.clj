(ns irresponsible.thyroid-test
  (:require [clojure.test :as t :refer [deftest testing is]]
            [irresponsible.domiscuity.dom :as dom]
            [irresponsible.domiscuity.parser :as dom-parser]
            [irresponsible.thyroid :as thyroid])
  (:import [clojure.lang ExceptionInfo]
           [org.thymeleaf.dialect IDialect]
           [org.thymeleaf.standard StandardDialect]
           [org.thymeleaf.processor IProcessor]
           [org.thymeleaf.templatemode TemplateMode]
           [org.thymeleaf.templateresolver
            ITemplateResolver TemplateResolution
            FileTemplateResolver StringTemplateResolver]))

(deftest test-template-resolver
  (testing "file resolver"
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
                 (thyroid/template-resolver {:type :invalid}))))

  (testing "defining a custom resolver"
    (defmethod thyroid/template-resolver :custom
      [_]
      (reify ITemplateResolver
        (getName [this] "custom")
        (getOrder [this] 0)
        (resolveTemplate [this conf owner-template template template-resolution-attrs]
          (TemplateResolution. nil TemplateMode/RAW nil))))
    (is (thyroid/template-resolver {:type :custom}))))

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

(deftest test-processor?
  (is (thyroid/processor? (thyroid/process-by-tag
                           {:prefix "clj", :name "p", :handler identity})))
  (is (thyroid/processor? (reify IProcessor
                            (getPrecedence [this] 420)
                            (getTemplateMode [this] nil))))
  (is (not (thyroid/processor? (thyroid/template-resolver {:type :string}))))
  (is (not (thyroid/processor? nil))))

(deftest test-process-by-tag
  (let [tag-proc (thyroid/process-by-tag {:prefix "clj"
                                          :use-prefix? true
                                          :name "p"
                                          :precedence 420
                                          :handler identity})]
    (testing "instance of IProcessor"
      (is (instance? IProcessor tag-proc)))

    (testing "attributes set correctly"
      (is (= 420 (.getPrecedence tag-proc)))
      (is (= "{clj:p,clj-p}"
             (str (.getMatchingElementName tag-proc)))))

    (testing "has no attribute name matcher"
      (is (nil? (.getMatchingAttributeName tag-proc)))))

  (testing "no prefix when use-prefix? is false"
    (let [tag-proc (thyroid/process-by-tag {:prefix "hello"
                                            :use-prefix? false
                                            :name "p"
                                            :handler identity})]
      (is (= "{p}" (str (.getMatchingElementName tag-proc))))))

  (testing "optional parameters omitted"
    (let [attr-proc (thyroid/process-by-tag
                     {:prefix "clj", :name "p", :handler identity})]
      (is (= StandardDialect/PROCESSOR_PRECEDENCE (.getPrecedence attr-proc))))))

(deftest test-process-by-attrs
  (let [attr-proc (thyroid/process-by-attrs
                   {:prefix "hello"
                    :handler identity
                    :attr-name "sayto"
                    :attr-prefix? true
                    :tag-name "p"
                    :tag-prefix? true
                    :remove? true
                    :precedence 420})]
    (testing "instance of IProcessor"
      (is (instance? IProcessor attr-proc)))

    (testing "attributes set correctly"
      (is (= 420 (.getPrecedence attr-proc)))
      (is (= "{hello:p,hello-p}" (str (.getMatchingElementName attr-proc))))
      (is (= "{hello:sayto,data-hello-sayto}"
             (str (.getMatchingAttributeName attr-proc))))))

  (testing "optional parameters omitted"
    (let [attr-proc (thyroid/process-by-attrs {:prefix "hello"
                                               :attr-name "sayto"
                                               :handler identity})]
      (is (nil? (.getMatchingElementName attr-proc)))
      (is (= StandardDialect/PROCESSOR_PRECEDENCE (.getPrecedence attr-proc)))))

  (testing "no tag name specified"
    (let [attr-proc (thyroid/process-by-attrs
                     {:prefix "hello"
                      :handler identity
                      :attr-name "sayto"
                      :prefix-attr? true})]
      (is (nil? (.getMatchingElementName attr-proc)))))

  (testing "prefix-tag? is false"
    (let [attr-proc (thyroid/process-by-attrs
                     {:prefix "hello"
                      :handler identity
                      :attr-name "sayto"
                      :prefix-attr? true
                      :tag-name "p"
                      :prefix-tag? false})]
      (is (= "{p}" (str (.getMatchingElementName attr-proc))))))

  (testing "no attribute prefix when prefix-attr? is false"
    (let [attr-proc (thyroid/process-by-attrs
                     {:prefix "hello"
                      :handler identity
                      :attr-name "sayto"
                      :prefix-attr? false})]
      (is (= "{sayto}" (str (.getMatchingAttributeName attr-proc)))))))

(deftest test-dialect
  (let [d (thyroid/dialect {:name "clojure" :prefix "clj"
                            :handler identity :precedence 420})]
    (testing "instance of IDialect"
      (is (instance? IDialect d)))

    (testing "attributes set correctly"
      (is (= "clojure" (.getName d)))
      (is (= "clj" (.getPrefix d)))
      (is (= 420 (.getDialectProcessorPrecedence d)))))

  (testing "optional parameters omitted"
    (let [d (thyroid/dialect {:name "clojure" :prefix "clj" :handler identity})]
      (is (= StandardDialect/PROCESSOR_PRECEDENCE
             (.getDialectProcessorPrecedence d)))))

  (testing "ensures that returned processors from handler are processors"
    (let [handlers [identity
                    (fn [_] #{"a" "b" "c"})
                    (fn [_] #{(thyroid/template-resolver {:type :string})})]]
      (doseq [h handlers]
        (let [d (thyroid/dialect {:name "clojure" :prefix "clj" :handler h})]
          (is (thrown? ExceptionInfo (.getProcessors d "clj"))))))))

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
          (is (= s "<h1>Hello World</h1>"))))))

  (testing "with a custom dialect"
    (let [handler (fn [prefix]
                    #{(thyroid/process-by-attrs
                       {:prefix prefix
                        :attr-name "sayto"
                        :remove? true
                        :handler (fn [_ _ _ attr-val struct]
                                   (.setBody struct (str "Hello, " attr-val) true))})
                      (thyroid/process-by-tag
                       {:name "greet"
                        :prefix prefix
                        :handler (fn [_ _ struct]
                                   (.setBody struct "Hello there!" true))})})
          dialect (thyroid/dialect {:name "Hello Dialect"
                                    :prefix "hello"
                                    :handler handler})
          engine (thyroid/make-engine {:resolvers [(thyroid/template-resolver
                                                    {:type :string :cache? false})]
                                       :dialects [dialect]})]
      (testing "attribute processor"
        (is (= "<p>Hello, World</p>"
               (thyroid/render engine "<p hello:sayto=\"World\">Hiya!</p>")))
        (is (= "<p>Hello, World</p>"
               (thyroid/render engine "<p data-hello-sayto=\"World\">Hiya!</p>"))))

      (testing "tag processor"
        (is (= "<hello:greet>Hello there!</hello:greet>"
               (thyroid/render engine "<hello:greet/>" {})))
        (is (= "<hello-greet>Hello there!</hello-greet>"
               (thyroid/render engine "<hello-greet/>" {})))))))
