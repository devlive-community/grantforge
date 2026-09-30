import { FontAwesomeIcon } from '@fortawesome/vue-fontawesome'
import { library } from '@fortawesome/fontawesome-svg-core'
import {
  faEdit,
  faHome,
  faLocationArrow,
  faPlus,
  faRightToBracket,
  faSignOut,
  faTrash,
  faUser,
  faUserPlus
} from '@fortawesome/free-solid-svg-icons'

const createIcons = (app: any) => {
  library.add(faHome, faRightToBracket, faUserPlus, faUser, faSignOut, faTrash, faLocationArrow, faPlus, faEdit)
  app.component('font-awesome-icon', FontAwesomeIcon)
}

export {
  createIcons
}
