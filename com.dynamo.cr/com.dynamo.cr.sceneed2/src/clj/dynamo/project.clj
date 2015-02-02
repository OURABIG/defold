(ns dynamo.project
  "Define the concept of a project, and its Project node type. This namespace bridges between Eclipse's workbench and
ordinary paths."
  (:require [clojure.java.io :as io]
            [plumbing.core :refer [defnk fnk]]
            [schema.core :as s]
            [dynamo.system :as ds]
            [dynamo.file :as file]
            [dynamo.node :as n]
            [dynamo.property :as dp]
            [dynamo.selection :as selection]
            [dynamo.types :as t]
            [dynamo.ui :as ui]
            [dynamo.util :refer :all]
            [internal.clojure :as clojure]
            [internal.ui.dialogs :as dialogs]
            [eclipse.resources :as resources]
            [service.log :as log])
  (:import [org.eclipse.core.resources IContainer IFile IProject IResource IResourceChangeListener]
           [org.eclipse.core.runtime IProgressMonitor]
           [org.eclipse.ui PlatformUI IEditorSite]
           [org.eclipse.ui.internal.registry FileEditorMapping EditorRegistry]))

(set! *warn-on-reflection* true)

(defn register-filetype
  [extension default?]
  (ui/swt-safe
    (let [reg ^EditorRegistry (.getEditorRegistry (PlatformUI/getWorkbench))
          desc (.findEditor reg "com.dynamo.cr.sceneed2.scene-editor")
          mapping (doto
                    (FileEditorMapping. extension)
                    (.addEditor desc))
          all-mappings (.getFileEditorMappings reg)]
      (when default? (.setDefaultEditor mapping desc))
      (.setFileEditorMappings reg (into-array (concat (seq all-mappings) [mapping]))))))

(defn register-node-type
  [filetype node-type]
  (ds/update-property (ds/current-scope) :node-types assoc filetype node-type))

(defn register-editor
  [filetype editor-builder]
  (ds/update-property (ds/current-scope) :handlers assoc-in [:editor filetype] editor-builder)
  (register-filetype filetype true))

(defn register-presenter
  [property presenter]
  (ds/update-property (ds/current-scope) :presenter-registry dp/register-presenter property presenter))

(defn- editor-for [project-scope ext]
  (or
    (get-in project-scope [:handlers :editor ext])
    (fn [& _] (throw (ex-info (str "No editor has been registered that can handle file type " (pr-str ext)) {})))))

(defn node?! [n kind]
  (assert (satisfies? t/Node n) (str kind " functions must return a node. Received " (type n) "."))
  n)

(n/defnode Placeholder
  "A Placeholder node represents a file-based asset that doesn't have any specific
behavior."
  (inherits n/ResourceNode)
  (output content s/Any (fnk [] nil)))

(defn- new-node-for-path
  [project-node path type-if-not-registered]
  (n/construct
    (get-in project-node [:node-types (t/extension path)] type-if-not-registered)
    :filename path))

(defn load-resource
  "Load a resource, usually from file. This will create a node of the appropriate type (as defined by
`register-node-type` and send it a :load message."
  [project-node path]
  (ds/transactional
    (ds/in project-node
      (ds/add
        (new-node-for-path project-node path Placeholder)))))

(n/defnode CannedProperties
  (property rotation     s/Str    (default "twenty degrees starboard"))
  (property translation  s/Str    (default "Guten abend."))
  (property some-vector  t/Vec3   (default [1 2 3]))
  (property some-integer s/Int    (default 42))
  (property background   dp/Color (default [0x4d 0xc0 0xca])))

(defn- build-editor-node
  [project-node site path content-node]
  (let [editor-factory (editor-for project-node (t/extension path))]
    (node?! (editor-factory project-node site content-node) "Editor")))

(defn- build-selection-node
  [editor-node selected-nodes]
  (ds/in editor-node
    (let [selection-node  (ds/add (n/construct selection/Selection))
          properties-node (ds/add (n/construct CannedProperties :rotation "e to the i pi"))]
      (doseq [node selected-nodes]
        (ds/connect node :self selection-node :selected-nodes))
      selection-node)))

(defn make-editor
  [project-node path ^IEditorSite site]
  (let [[editor-node selection-node] (ds/transactional
                                       (ds/in project-node
                                         (let [content-node   (t/lookup project-node path)
                                               editor-node    (build-editor-node project-node site path content-node)
                                               selection-node (build-selection-node editor-node [content-node])]
                                           (when ((t/inputs editor-node) :presenter-registry)
                                             (ds/connect project-node :presenter-registry editor-node :presenter-registry))
                                           (when (and ((t/inputs editor-node) :saveable) ((t/outputs content-node) :save))
                                             (ds/connect content-node :save editor-node :saveable))
                                           (when (and ((t/inputs editor-node) :dirty) ((t/outputs content-node) :dirty))
                                             (ds/connect content-node :dirty editor-node :dirty))
                                           [editor-node selection-node])))]
    (.setSelectionProvider site selection-node)
    editor-node))

(defn- send-project-scope-message
  [txn graph self label kind]
  (doseq [id (:nodes-added txn)]
    (ds/send-after {:_id id} {:type :project-scope :scope self})))

(defn project-enclosing
  [node]
  (first (ds/query (:world-ref node) [[:_id (:_id node)] '(output :self) (list 'protocol `ProjectRoot)])))

(defn scope-enclosing
  [node]
  (first (ds/query (:world-ref node) [[:_id (:_id node)] '(output :self) (list 'protocol `t/NamingContext)])))

(defn nodes-in-project
  "Return a lazy sequence of all nodes in this project. There is no
guaranteed ordering of the sequence."
  [project-node]
  (ds/query (:world-ref project-node) [[:_id (:_id project-node)] '(input :nodes)]))

(defn nodes-with-filename
  "Return a lazy sequence of all nodes in the project that match this filename.
There is no guaranteed ordering of the sequence."
  [project-node path]
  (ds/query (:world-ref project-node) [[:_id (:_id project-node)] '(input :nodes) [:filename path]]))

(defn nodes-with-extensions
  [project-node extensions]
  (let [extensions (into #{} extensions)
        pred (fn [node] (and (:filename node)
                          (some #{(t/extension (:filename node))} extensions)))]
    (into #{}
      (filter pred (nodes-in-project project-node)))))

(defn select-resources
  ([project-node extensions]
    (select-resources project-node extensions "Select resource" false))
  ([project-node extensions title]
    (select-resources project-node extensions title false))
  ([project-node extensions title multiselect?]
    (dialogs/resource-selection-dialog title multiselect? (nodes-with-extensions project-node extensions))))

(defprotocol ProjectRoot)

(defn project-root? [node]
  (satisfies? ProjectRoot node))

(n/defnode Project
  (inherits n/Scope)

  (trigger notify-content-nodes :modified send-project-scope-message)

  (property tag                s/Keyword (default :project))
  (property eclipse-project    IProject)
  (property content-root       IContainer)
  (property branch             s/Str)
  (property presenter-registry t/Registry)
  (property node-types         {s/Str s/Symbol})
  (property handlers           {s/Keyword {s/Str s/fn-schema}})

  ProjectRoot
  t/NamingContext
  (lookup [this name]
    (let [path (if (instance? dynamo.file.ProjectPath name) name (file/make-project-path this name))]
      (first (ds/query (:world-ref this) [[:_id (:_id this)] '(input :nodes) [:filename path]]))))

  t/FileContainer
  (node-for-path [this path]
    (new-node-for-path this (file/make-project-path this path) Placeholder))

  (on :destroy
    (ds/delete self)))

(defn project-root-node
  "Finds and returns the ProjectRoot node starting from any node."
  [node]
  (when node
    (if (project-root? node)
      node
      (recur (scope-enclosing node)))))

(defn load-resource-nodes
  [project-node resources ^IProgressMonitor monitor]
  (ds/transactional
    (let [eclipse-project ^IProject (:eclipse-project project-node)]
      (ds/in project-node
        [project-node (doall
                        (for [resource resources
                              :let [p (file/make-project-path project-node resource)]]
                          (monitored-work monitor (str "Scanning " (t/local-name p))
                            (load-resource project-node p))))]))))

(defn load-project
  [^IProject eclipse-project branch ^IProgressMonitor monitor]
  (ds/transactional
    (ds/add
      (n/construct Project
        :eclipse-project eclipse-project
        :content-root (.getFolder eclipse-project "content")
        :branch branch
        :node-types {"clj" clojure/ClojureSourceNode}))))

(defn- post-load
  [message project-node resource-nodes]
  (doseq [resource-node resource-nodes]
    (log/logging-exceptions (str message (:filename resource-node))
      (when (satisfies? t/MessageTarget resource-node)
        (ds/in project-node
          (t/process-one-event resource-node {:type :load :project project-node}))))))

(defn load-project-and-tools
  [^IProject eclipse-project branch ^IProgressMonitor monitor]
  (let [project-node    (load-project eclipse-project branch monitor)
        resources       (group-by clojure/clojure-source? (filter #(instance? IFile %) (resources/resource-seq eclipse-project)))
        clojure-sources (get resources true)
        non-sources     (get resources false)]
    (monitored-task monitor "Scanning project" (inc (count resources))
      (apply post-load "Compiling"          (load-resource-nodes (ds/refresh project-node) clojure-sources monitor))
      (apply post-load "Loading asset from" (load-resource-nodes (ds/refresh project-node) non-sources     monitor)))
    project-node))

(defn- unload-nodes
  [nodes]
  (ds/transactional
    (doseq [n nodes]
      (ds/send-after n {:type :unload}))))

(defn- replace-nodes
  [project-node nodes-to-replace f]
  (ds/transactional
    (doseq [old nodes-to-replace
            :let [new (f old)]]
      (ds/become old new)
      (ds/send-after old {:type :load :project project-node}))))

(defn- add-or-replace?
  [project-node resource]
  (let [[node] (nodes-with-filename project-node (file/make-project-path project-node resource))]
    (cond
      (not (nil? node))                   :replace-existing
      (clojure/clojure-source? resource)  :load-clojure
      :else                               :load-other)))

(defn- update-added-resources
  [project-node {:keys [added]}]
  (when (not-empty added)
    (println :update-added-resources added))
  (let [with-placeholders (group-by #(add-or-replace? project-node %) added)
        replacements      (mapcat #(nodes-with-filename project-node (file/make-project-path project-node %)) (:replace-existing with-placeholders))]
    (unload-nodes replacements)
    (replace-nodes project-node replacements #(t/node-for-path project-node (:filename %)))
    (apply post-load "Compiling"          (load-resource-nodes (ds/refresh project-node) (:load-clojure with-placeholders) nil))
    (apply post-load "Loading asset from" (load-resource-nodes (ds/refresh project-node) (:load-other   with-placeholders) nil))
    project-node))

(defn- update-deleted-resources
  [project-node {:keys [deleted]}]
  (when (not-empty deleted)
    (println :update-deleted-resources deleted))
  (let [nodes-to-delete (mapcat #(nodes-with-filename project-node (file/make-project-path project-node %)) deleted)]
    (unload-nodes nodes-to-delete)
    (replace-nodes project-node nodes-to-delete #(new-node-for-path project-node (:filename %) Placeholder)))
  project-node)

(defn- update-changed-resources
  [project-node {:keys [changed]}]
  (when (not-empty changed)
    (println :update-changed-resources changed))
  (let [nodes-to-replace (map #(first (nodes-with-filename project-node (file/make-project-path project-node %))) changed)]
    (unload-nodes nodes-to-replace)
    (replace-nodes project-node nodes-to-replace #(new-node-for-path project-node (:filename %) Placeholder))))

(defn- update-resources
  [project-node changeset]
  (-> (ds/refresh project-node)
    (update-added-resources changeset)
    (update-deleted-resources changeset)
    (update-changed-resources changeset)))

; ---------------------------------------------------------------------------
; Lifecycle, Called during connectToBranch
; ---------------------------------------------------------------------------
(defn open-project
  "Called from com.dynamo.cr.editor.Activator when opening a project. You should not call this function directly."
  [eclipse-project branch ^IProgressMonitor monitor]
  (let [project-node (load-project-and-tools eclipse-project branch monitor)
        listener     (resources/listen-for-change #(update-resources project-node %))]
    (resources/listen-for-close project-node listener)))
