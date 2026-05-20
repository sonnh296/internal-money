import { test as base } from '@playwright/test'
import { readUsersCache, type CachedUser } from '../helpers/env'

type UserFixtures = {
  user1: CachedUser
  user2: CachedUser
}

export const test = base.extend<UserFixtures>({
  user1: async ({}, use) => {
    const cache = readUsersCache()
    if (!cache?.user1) throw new Error('user1 missing — run global-setup')
    await use(cache.user1)
  },
  user2: async ({}, use) => {
    const cache = readUsersCache()
    if (!cache?.user2) throw new Error('user2 missing — run global-setup')
    await use(cache.user2)
  },
})

export { expect } from '@playwright/test'
