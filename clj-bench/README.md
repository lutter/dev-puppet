# pe-bench

Tools to make it easier to talk to various PE services from the repl.

## Usage

1. Copy `/etc/puppetlabs/puppet/ssl` from your PE master to somewhere on
   your local system
1. Copy `pe-config.clj.sample` to `pe-config.clj` and edit it according to
   your local setup (it should be enough to change `basedir` and
   `puppet-master`)
1. Load `pe-config.clj` into your repl and then `src/pe-bench.clj`
1. Save yourself some typing: `(require '[pe-bench :as p])`

You can now do fun things like these in the repl:

```clojure
;; Make facts for 3 good and 3 bad hosts
(p/pdb-replace-facts "good" (p/make-facts 3 "good"))
(p/pdb-replace-facts "bad" (p/make-facts 3 "bad"))

;; Query that stuff
(map :certname (p/pdb-nodes '("~" certname "good.*")))
(count (p/pdb-facts '("~" certname "good.*")))
(distinct (map :certname (p/pdb-facts '("~" certname "good.*"))))

;; Talk to the NC
(p/nc-groups)
(p/nc-create-group
  {:parent "00000000-0000-4000-8000-000000000000"
   :name "mygroup"
   :rule ["and" ["~" "name" "bad.*"]]})
(p/nc-delete-group "7864b178-cdb3-4690-9702-4e79575322f7")
```

Of course, it would be even better if more of our API's were accessible in
this way - that's where you and your pull requests come in ;)

## License

Copyright © 2015 FIXME
