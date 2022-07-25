(ns dashboard.regs.sign-in
  (:require
   [re-frame.core :as rf :refer [subscribe]]
   [inmesh.core :as mesh :refer [in]]
   [inmesh.re-frame :refer [reg-sub dispatch]]))

;;; Subs

(rf/reg-sub
 :sign-in/errors
 :<- [:errors]
 (fn [errors _]
   (:sign-in errors)))

;;; Events

(rf/reg-event-fx
 :sign-in
 (fn [{:keys [db]} [_ {:keys [userid password remember?]}]]
   (if (= password "top-secret")
     {:db (-> db
              (assoc-in [:auth :user-id] userid)
              (assoc-in [:auth :remember?] remember?))
      :navigate! [:routes/home]}
     {:db (assoc-in db [:errors :sign-in]
                    {:password "Wrong Password! (should be \"top-secret\")"})})))

(rf/reg-event-db
 :sign-in/clear-errors
 (fn [db [_ field]]
   (update-in db [:errors :sign-in] dissoc field)))
