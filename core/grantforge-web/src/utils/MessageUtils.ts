import { ErrorValidationEntity } from '@/entity/ErrorValidationEntity'
import Message from 'view-shadcn-ui'

export class MessageUtils {
  handlerError (error: ErrorValidationEntity) {
    let message: string | undefined = error.error
      ?.map(value => value.field + ' : ' + value.message)
      .join('\n')
    message = message || error as unknown as string
    Message.error(message)
  }
}

export default new MessageUtils()
