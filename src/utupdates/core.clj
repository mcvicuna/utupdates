(ns utupdates.core
  (:require [net.cgrand.enlive-html :as html])
  (:require [clj-http.client :as client])
  (:require [clojure.string :as str])
  (:require [clojure.java.jdbc :refer :all])
  (:import (java.net MalformedURLException)))


(def db
  {:classname   "org.sqlite.JDBC"
   :subprotocol "sqlite"
   :subname     "resources/places.sqlite"
   })

(def html-top "<!DOCTYPE html>\n<html lang=\"en\">\n<head>\n    <meta charset=\"UTF-8\">\n    <title>Test</title>")
(def html-bot "</head>\n<body>\n\n</body>\n</html>")
(def html-unit "<p>::%s</p>\n %s")
(def html-link "<p><a href=%s>%s</a></p>\n")

(defn prepend [a b]
  (str b a))

(defn get-units [a]
  (try
    (nth (str/split (first ((nth ((first (html/select a [:ul.yt-lockup-meta-info])) :content) 1) :content)) #" ") 1)
    (catch Exception e {})
    )
  )

(defn get-info [a & { :keys  [host] :or {host nil} }]
  (try
    (update-in (select-keys ((first (html/select a [:a.spf-link])) :attrs) [:title :href]) [:href] prepend host)
    (catch Exception e {})
    )
  )

(defn get-videos [a]
  (html/select a [:div.yt-lockup-dismissable])
  )

(defn get-links [a]
  (map #( assoc nil (keyword (get-units %1)) [(get-info %1 :host "https://www.youtube.com")])  a)
  )

(defn get-links-by-unit [a]
  (apply merge-with into a))

(defn merge-links-by-unit [a]
  (apply merge-with into a )
  )


(def urls (map #(%1 :url)
               (query db "SELECT * FROM moz_places")))

(defn filter-urls [urls host]
  (for [url urls :let [parsed-url ( try
                                    (client/parse-url url)
                                    (catch MalformedURLException e (client/parse-url "http://www.ignore.com"))
                                    )]
        :when (= host (parsed-url :server-name))]
         url
    )
)
(defn get-page [url]
  (try
    (client/get url)
    (catch Exception e {:body " "})
    ))


(defn get-pages [urls & { :keys [pause-length] :or {pause-length 300} }]
  (doall (for [url urls :let [pause (Thread/sleep pause-length) ]] (get-page url)
    ))
)

(defn get-html-snippet [page]
  (html/html-snippet (page :body))
  )


(defn get-snippets [pages]
  (map #(get-html-snippet %1) pages)
  )


(defn links->html [links filename]
  (spit filename (str/join [ html-top
            (str/join (for [[k v] links] (format html-unit k (str/join (for [l v] (format html-link (l :href) (l :title)))))))
            html-bot]))
  )
(defn merge-pages [snippets]
  (merge-links-by-unit (map #(get-links-by-unit (get-links (get-videos %1))) snippets))
)



(defn run []
  (links->html (merge-pages (get-snippets (get-pages (filter-urls urls "www.youtube.com")))) (.format (java.text.SimpleDateFormat. "yyyy_MM_dd") (java.util.Date.)))
  )

