export class ErrorMessageEntity {
  field: string | undefined
  message: string | undefined
}

export class ErrorValidationEntity {
  count: number | undefined
  error: Array<ErrorMessageEntity> | undefined
}
