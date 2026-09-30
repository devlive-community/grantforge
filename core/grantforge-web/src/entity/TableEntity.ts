export class TableColumnEntity {
  title: string | undefined
  key: string | undefined
  slot?: string | undefined
  tooltip?: boolean | undefined
  fixed?: string | undefined
  width? = 100

  constructor (title: string | undefined, key: string | undefined, slot?: string | undefined, tooltip?: boolean | undefined, fixed?: string | undefined, width?: number) {
    this.title = title
    this.key = key
    this.slot = slot
    this.tooltip = tooltip
    this.fixed = fixed
    if (this.width) {
      this.width = width
    }
  }
}
