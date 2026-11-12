import { UI_V2 } from '../config/ui'

type ViewLoader = () => Promise<unknown>

export function pickView(legacy: ViewLoader, v2: ViewLoader): ViewLoader {
  return UI_V2 ? v2 : legacy
}

export function pickLayout(legacy: ViewLoader, v2: ViewLoader): ViewLoader {
  return UI_V2 ? v2 : legacy
}
