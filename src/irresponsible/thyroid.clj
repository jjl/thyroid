(ns irresponsible.thyroid
  (:require [clojure.spec.alpha :as s]
            [clojure.walk :as walk]
            [irresponsible.spectra :as ss])
  (:import [org.thymeleaf TemplateEngine]
           [org.thymeleaf.context Context]
           [org.thymeleaf.dialect IDialect]
           [org.thymeleaf.model AttributeValueQuotes IStandaloneElementTag]
           [org.thymeleaf.standard StandardDialect]
           [org.thymeleaf.templatemode TemplateMode]
           [org.thymeleaf.templateresolver ITemplateResolver
            FileTemplateResolver StringTemplateResolver]
           [irresponsible.thyroid
            ClojureTagProcessor ClojureAttrProcessor ClojureDialect]))

(defn- ensure-trailing-slash [path]
  (-> (clojure.java.io/file path)
      (.getPath)
      (str "/")))

(defn- set-cache-attrs!
  [resolver {:keys [^long cache-ttl ^boolean cache?]}]
  (when cache-ttl
    (.setCacheTTLMs resolver cache-ttl))
  (when (some? cache?)
    (.setCacheable resolver cache?)))

(s/def ::prefix (s/and string? seq))
(s/def ::suffix (s/and string? seq))
(s/def ::cache-ttl pos?)
(s/def ::cache? boolean?)
(s/def ::file-template-resolver
  (s/keys :req-un [::prefix ::suffix] :opt-un [::cache-ttl ::cache?]))

(s/def ::string-template-resolver
  (s/keys :opt-un [::cache-ttl ::cache?]))

(defmulti template-resolver-spec :type :default ::default)

(defmethod template-resolver-spec :file [_] ::file-template-resolver)
(defmethod template-resolver-spec :string [_] ::string-template-resolver)
;; FIXME: do nothing, end users shouldn't need to use spec
(defmethod template-resolver-spec ::default [{:keys [type]}]
  (throw (ex-info (str "Unknown template resolver type: " type) {:got type})))

(defmulti template-resolver
  #(:type (ss/assert! ::template-resolver %))
  :default ::default)

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

(s/def ::template-resolver (s/multi-spec template-resolver-spec ::resolver))

(s/def ::resolver #(instance? ITemplateResolver %))
(s/def ::resolvers (s/coll-of ::resolver :min-count 1 :into []))

(s/def ::dialect #(instance? IDialect %))
(s/def ::dialects (s/coll-of ::dialect :into []))
(s/def ::thyroid (s/keys :req-un [::resolvers] :opt-un [::dialects]))

(defn make-engine
  "Creates a thymeleaf TemplateEngine with the given properties.
  args: [opts]:"
  [opts]
  (let [{:keys [resolvers dialects]} (ss/assert! ::thyroid opts)
        e (TemplateEngine.)]
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

(defn dialect
  "Creates a new dialect with the given properties
   args: [opts] ; map, keys:
     :name - mandatory string, the human readable name of this dialect
     :prefix - mandatory string, the xml namespace of any tags we declare
     :handler - mandatory function, args: [prefix], returns: set
     :precedence - optional int, defaults to equal precedence with the standard dialect
   returns: ClojureDialect"
  [opts]
  (let [{:keys [name prefix handler precedence meta]
         :or {precedence StandardDialect/PROCESSOR_PRECEDENCE}} (ss/assert! ::dialect-opts opts)]
    (ClojureDialect. name prefix handler precedence meta)))

(s/def ::use-prefix? boolean?)
(s/def ::by-tag-opts (s/keys :req-un [::prefix ::name ::handler]
                             :opt-un [::precedence ::meta]))

(defn process-by-tag
  "Creates an element processor that is triggered by a tag name
   args: [opts] ; map, keys;
     :prefix - mandatory string, the prefix of the dialect
     :name - mandatory string, the tag name
     :use-prefix? - whether the tag should be recognised with or without the prefix
     :precedence - optional int, defaults to equal precedence with the standard dialect
     :handler - mandatory function, args: [context tag structure-handler], void
   returns: ClojureTagProcessor"
  [opts]
  (let [{:keys [prefix name use-prefix? precedence handler meta]
         :or {precedence 1000}} (ss/assert! ::by-tag-opts opts)]
    (ClojureTagProcessor. prefix name use-prefix? precedence handler meta)))

(s/def ::tag-name string?)
(s/def ::attr-name string?)
(s/def ::prefix-tag? boolean?)
(s/def ::prefix-attr? boolean?)
(s/def ::remove? boolean?)
(s/def ::by-attr-opts (s/keys :req-un [::prefix ::tag-name ::attr-name
                                       ::prefix-tag? ::prefix-attr?
                                       ::remove? ::handler]
                              :opt-un [::precedence ::meta]))

(defn process-by-attrs
  "Creates an element processor that is triggered by an attribute name and optionally a tag name
   args: [opts] ; map, keys;
     :prefix - mandatory string, the prefix of the dialect
     :tag-name - mandatory string, the tag name
     :attr-name - mandatory string, the attributethis name
     :prefix-tag? - mandatory bool, whether the tag should be recognised with or without the prefix
     :prefix-attr? - mandatory bool, whether the attr should be recognised with or without the prefix
     :remove? - mandatory bool, whether to remove this attribute from the tag
     :precedence - mandatory int, defaults to equal precedence with the standard dialect
     :handler - mandatory function, args: [context tag attr-name attr-val structure-handler], void
   returns: ClojureAttrProcessor"
  [opts]
  (let [{:keys [prefix tag-name attr-name prefix-tag? prefix-attr? remove? precedence handler meta]} (ss/assert! ::by-attr-opts opts)]
    (ClojureAttrProcessor. prefix tag-name attr-name prefix-tag? prefix-attr? remove? precedence handler meta)))

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
