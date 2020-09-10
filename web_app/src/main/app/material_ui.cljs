(ns app.material-ui
  (:require
   [com.fulcrologic.fulcro.algorithms.react-interop :as interop]

   ["@material-ui/core" :as mui]
   ["@material-ui/icons" :as icons]))

(def tabs (interop/react-factory mui/Tabs))
(def tab (interop/react-factory mui/Tab))
(def button (interop/react-factory mui/Button))
(def listm (interop/react-factory mui/List))
(def list-item (interop/react-factory mui/ListItem))
(def list-item-text (interop/react-factory mui/ListItemText))
(def list-item-avatar (interop/react-factory mui/ListItemAvatar))
(def avatar (interop/react-factory mui/Avatar))

(def folder-icon (interop/react-factory icons/FolderIcon))
