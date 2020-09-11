(ns app.rmwc
  (:require
   [com.fulcrologic.fulcro.algorithms.react-interop :as interop]

   ["@rmwc/formfield" :as rm-form-field]
   ["@rmwc/textfield" :as rm-text-field]
   ["@rmwc/typography" :as rm-typography]
   ["@rmwc/avatar" :as rm-avatar]
   ["@rmwc/top-app-bar" :as rm-top-app-bar]
   ["@rmwc/list" :as rm-list]
   ["@rmwc/drawer" :as rm-drawer]
   ["@rmwc/switch" :as rm-switch]
   ["@rmwc/icon-button" :as rm-icon-button]
   ["@rmwc/icon" :as rm-icon]
   ["@rmwc/tabs" :as rm-tabs]
   ["@rmwc/button" :as rm-button]))

(def form-field (interop/react-factory rm-form-field/FormField))
(def text-field (interop/react-factory rm-text-field/TextField))
(def t (interop/react-factory rm-typography/Typography))
(def avatar (interop/react-factory rm-avatar/Avatar))
(def top-bar-fixed-adjust (interop/react-factory rm-top-app-bar/TopAppBarFixedAdjust))
(def simple-top-app-bar (interop/react-factory rm-top-app-bar/SimpleTopAppBar))
(def simple-list-item (interop/react-factory rm-list/SimpleListItem))
(def collapsible-list (interop/react-factory rm-list/CollapsibleList))
;; (def list-item-meta (interop/react-factory rm-list/ListItemMeta))
;; (def list-item-graphic (interop/react-factory rm-list/ListItemGraphic))
;; (def list-item-text (interop/react-factory rm-list/ListItemText))
;; (def list-item-primary-text (interop/react-factory rm-list/ListItemPrimaryText))
;; (def list-item-secondary-text (interop/react-factory rm-list/ListItemSecondaryText))
;; (def list-item (interop/react-factory rm-list/ListItem))
(def list- (interop/react-factory rm-list/List))
(def drawer-content (interop/react-factory rm-drawer/DrawerContent))
(def drawer-subtitle (interop/react-factory rm-drawer/DrawerSubtitle))
(def drawer-title (interop/react-factory rm-drawer/DrawerTitle))
(def drawer-header (interop/react-factory rm-drawer/DrawerHeader))
(def drawer-app-content (interop/react-factory rm-drawer/DrawerAppContent))
(def drawer (interop/react-factory rm-drawer/Drawer))
(def switch (interop/react-factory rm-switch/Switch))
(def button (interop/react-factory rm-button/Button))
(def tab-bar (interop/react-factory rm-tabs/TabBar))
(def tab (interop/react-factory rm-tabs/Tab))
(def icon (interop/react-factory rm-icon/Icon))
(def icon-button (interop/react-factory rm-icon-button/IconButton))
