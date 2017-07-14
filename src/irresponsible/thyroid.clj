(ns irresponsible.thyroid
  (:require [clojure.spec.alpha :as s]
            [clojure.walk :as walk]
            [irresponsible.spectra :as ss])
  (:import [org.thymeleaf TemplateEngine]
           [org.thymeleaf.context Context]
           [org.thymeleaf.dialect AbstractProcessorDialect IDialect]
           [org.thymeleaf.processor IProcessor]
           [org.thymeleaf.processor.element
            AbstractAttributeTagProcessor
            AbstractElementTagProcessor]
           [org.thymeleaf.standard StandardDialect]
           [org.thymeleaf.templatemode TemplateMode]
           [org.thymeleaf.templateresolver
            FileTemplateResolver
            ITemplateResolver
            StringTemplateResolver]))

(defn- ensure-trailing-slash [path]
  (-> (clojure.java.io/file path)
      (.getPath)
      (str "/")))

(defn- set-cache-attrs!
  [^ITemplateResolver resolver {:keys [^long cache-ttl ^boolean cache?]}]
  (when cache-ttl
    (.setCacheTTLMs resolver cache-ttl))
  (when (some? cache?)
    (.setCacheable resolver cache?)))

(defmulti template-resolver :type :default ::default)

(defmethod template-resolver :file
  [{:keys [^String prefix ^String suffix] :as options}]
  (let [tr (doto (FileTemplateResolver.)
             (.setPrefix (ensure-trailing-slash prefix))
             (.setSuffix suffix))]
    (set-cache-attrs! tr options)
    tr))

(defmethod template-resolver :string
  [options]
  (let [sr (StringTemplateResolver.)]
    (set-cache-attrs! sr options)
    sr))

(defmethod template-resolver ::default
  [{:keys [type]}]
  (throw (ex-info (str "Unknown template resolver type: " type) {:got type})))

(defn make-engine
  "Creates a thymeleaf TemplateEngine with the given properties.
  args: [opts]:"
  [{:keys [resolvers dialects]}]
  (let [e (TemplateEngine.)]
    (doseq [d dialects]
      (.addDialect e d))
    (.setTemplateResolvers e (set resolvers))
    e))

(s/def ::meta map?)

(s/def ::name string?)
(s/def ::prefix string?)
(s/def ::precedence int?)
(s/def ::handler ifn?)

(s/def ::dialect-opts
  (s/keys :req-un [::name ::prefix ::handler]
          :opt-un [::precedence ::meta]))

(s/def ::dialect-processors
  (s/coll-of #(instance? IProcessor %) :into #{}))

(defn dialect
  "Creates a new dialect with the given properties
   args: [opts] ; map, keys:
     :name - mandatory string, the human readable name of this dialect
     :prefix - mandatory string, the xml namespace of any tags we declare
     :handler - mandatory function, args: [prefix], returns: set
     :precedence - optional int, defaults to equal precedence with the standard dialect
   returns: implementation of AbstractProcessorDialect and IObj"
  [opts]
  (let [{:keys [name prefix handler precedence meta]
         :or {precedence StandardDialect/PROCESSOR_PRECEDENCE}}
        (ss/assert! ::dialect-opts opts)]
    (proxy [AbstractProcessorDialect clojure.lang.IObj]
        [name prefix precedence]
      (meta [] meta)
      (withMeta [meta]
        (dialect (assoc opts :meta meta)))
      (getProcessors [prefix]
        (ss/assert! ::dialect-processors (handler prefix))))))

(defn process-by-tag
  "Creates an element processor that is triggered by a tag name
   args: [opts] ; map, keys;
     :prefix - mandatory string, the prefix of the dialect
     :name - mandatory string, the tag name
     :use-prefix? - optional bool, whether the tag should be recognised with or without the prefix,
     defaults to true
     :precedence - optional int, defaults to equal precedence with the standard dialect
     :handler - mandatory function, args: [context tag structure-handler], void
  returns: implementation of AbstractElementTagProcessor"
  [{:keys [prefix name use-prefix? precedence handler]
    :or {precedence StandardDialect/PROCESSOR_PRECEDENCE
         use-prefix? true}}]
  (proxy [AbstractElementTagProcessor]
      [TemplateMode/HTML prefix name use-prefix? nil false precedence]
    (doProcess [ctx tag struct-handler]
      (handler ctx tag struct-handler))))

(defn process-by-attrs
  "Creates an element processor that is triggered by an attribute name and optionally a tag name
   args: [opts] ; map, keys;
     :prefix - mandatory string, the prefix of the dialect
     :attr-name - mandatory string, the attributethis name
     :prefix-attr? - optional bool, whether the attr should be recognised with or without the prefix,
     defaults to true
     :handler - mandatory function, args: [context tag attr-name attr-val structure-handler], void
     :tag-name - optional string, the tag name
     :prefix-tag? - optional bool, whether the tag should be recognised with or without the prefix,
     defaults to true
     :remove? - optional bool, whether to remove this attribute from the tag, defaults to false
     :precedence - optional int, defaults to equal precedence with the standard dialect
   returns: implementation of AbstractAttributeTagProcessor"
  [{:keys [prefix tag-name attr-name prefix-tag? prefix-attr? remove? precedence handler]
    :or {precedence StandardDialect/PROCESSOR_PRECEDENCE
         prefix-attr? true
         prefix-tag? true
         remove? false}}]
  (proxy [AbstractAttributeTagProcessor]
      [TemplateMode/HTML prefix tag-name prefix-tag? attr-name prefix-attr? precedence remove?]
    (doProcess [ctx tag attr-name attr-val struct-handler]
      (handler ctx tag attr-name attr-val struct-handler))))

(defn munge-key
  "Returns a string that is valid for use as an identifier
  in thymeleaf, replacing dashes and spaces with underscores."
  [k]
  (-> (name k)
      (clojure.string/replace #"-+" "_")
      (clojure.string/replace #"\s+" "_")))

(defn munge-coll
  "Returns a collection that is valid for use when indexing
  in thymeleaf. This includes maps only, collections themselves
  will not be touched."
  [m]
  (let [f (fn [[k v]] [(munge-key k) v])]
    (walk/postwalk (fn [x]
                     (cond
                       (map? x) (into (empty x) (map f x))
                       (coll? x) (into (empty x) (map munge-coll x))
                       :else x))
                   m)))

(defn context
  "Returns a thymeleaf Context using a clojure map. Handles converting keys
  to valid thymeleaf identifiers. E.g. a-valid-var -> a_valid_var"
  [m]
  (let [^Context c (Context.)]
    (doseq [[k v] m]
      (.setVariable c
       (munge-key k)
       (if (coll? v) (munge-coll v) v)))
    c))

(defn render
  "Render a template `template` using `engine` as a string,
  using `data` as the context."
  [^TemplateEngine engine ^String template data]
  (let [^Context c (context data)]
    (.process engine template c)))
