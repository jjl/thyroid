The irresponsible clojure guild presents...

# Thyroid

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

# Render data to a string
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

You can also define your own template resolvers 

```clojure
(import '(org.thymeleaf.templatemode TemplateMode))
(import '(org.thymeleaf.templateresolver ITemplateResolver TemplateResolution))

;; Allow parsing plain strings
(defmethod t/template-resolver ::text
  [options]
  (reify ITemplateResolver
    (getName [this] (:name options))
    (getOrder [this] (:order options))
    (resolveTemplate [this conf owner-template template resolution-attrs]
      ...)))
```

## Contributors

* [James Laver](https://github.com/jjl)
* [Antonis Kalou](https://github.com/kalouantonis)

## Copyright and License

[MIT License](LICENSE)
