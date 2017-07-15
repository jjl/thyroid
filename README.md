The irresponsible clojure guild presents...

# Thyroid

[![Build Status](https://travis-ci.org/irresponsible/thyroid.svg?branch=master)](https://travis-ci.org/irresponsible/thyroid)

HTML5 templating made differently insane.

## Usage

```clojure
(require '[irresponsible.thyroid :as t])

(def resolver (t/template-resolver 
                {:type :file
                 :prefix "resources/templates/"
                 :suffix ".html"
                 :cache? false}))

(def engine (t/make-engine {:resolvers [resolver]}))

;; Render data to a string
(t/render engine "hello.html" {"title" "Hello World!"})
```

The template file is defined as

```html
<!-- resources/templates/hello.html -->
<html>
    <head>
        <title th:text="${title}">No title set</title>
    </head>
    <body>
        <h1 th:text="${title}">fake title</h1>
    </body>
</html>
```

### Template resolvers

You can define your own template resolvers like this

```clojure
(import '(org.thymeleaf.templatemode TemplateMode))
(import '(org.thymeleaf.templateresolver ITemplateResolver TemplateResolution))

;; Allow parsing plain strings
(defmethod t/template-resolver ::custom
  [options]
  (reify ITemplateResolver
    (getName [this] (:name options))
    (getOrder [this] (:order options))
    (resolveTemplate [this conf owner-template template resolution-attrs]
      ...)))
```

### Dialects

Adding dialects is a more involved process, this example is a copy of that
presented by the thymeleaf ["Say Hello! Extending Thymeleaf in 5 minutes" guide][1].

Each dialect expects a handler that returns a set of processors that will be
called when the dialect is being rendered.

[1]: http://www.thymeleaf.org/doc/articles/sayhelloextendingthymeleaf5minutes.html

```clojure
(import '(org.unbescape.html.HtmlEscape))

(defn say-hello-processor
  [prefix]
  (t/process-by-attrs
    {:prefix prefix
     :attr-name "sayto"
     :handler (fn [ctx tag attr-name attr-val struct-handler]
                (.setBody struct-handler 
                  (str "Hello, " (HtmlEscape/escapeHtml attr-val) "!")))))

(def my-dialect 
  (t/dialect 
    {:name "Hello Dialect" 
     :prefix "hello" 
     :handler (fn [prefix] 
                #{(say-hello-processor prefix)})}))
                
;; Render string template
(def engine (t/make-engine 
              {:resolvers [(thyroid/template-resolver {:type :string})]}))
(t/render engine "<p hello:sayto="World">Hi ya!</p>") 
```

The above example will render `<p>Hello, World!</p>`

## Contributors

* [James Laver](https://github.com/jjl)
* [Antonis Kalou](https://github.com/kalouantonis)
* [Kent Fredric](https://github.com/kentfredric)

## Copyright and License

[MIT License](LICENSE)
