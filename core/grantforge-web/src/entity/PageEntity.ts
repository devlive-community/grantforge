export class PageEntity {
  size = 10;
  page = 1;
}

export class PageResponseEntity {
  content: [] | undefined
  number: number | undefined
  numberOfElements: number | undefined
  size: number | undefined
  totalElements: number | undefined
  totalPages: number | undefined
}
