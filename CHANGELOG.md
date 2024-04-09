# Change Log

## [1.2.0]
- BREAKING: Bump dependencies

## [1.1.2]
- Add clj-kondo configs and export for better linting.

## [1.1.1]
- Bump lodash to fix dependabot high severity alert.

## [1.1.0]
- Add query parameter "ws" to allow open a specific workspace.
- Add theme selector and dark mode.

## [1.0.17]
- Fix npm packages security vulnerabilities.

## [1.0.16]
- Fix shadow cljs target. Use shadow-cljs helper to find test namespaces

## [1.0.15]
- Fix shadow cljs target (manually add back fn removed from Shadow)

## [1.0.14]
- Remove accidental usage of ghostwheel

## [1.0.13]
- Fix deps

## [1.0.12]
- Bump Fulcro 3 deps

## [1.0.11]
- Fix Fulcro 3 card refresh

## [1.0.10]
- Improved tab styling 

## [1.0.9]
- Add support for Fulcro 3
- Prevent update errors when workspaces is not initialized

## [1.0.8]
- Add disable state style to workspaces ui button
- Accept ::f.portal/root-node-props on Fulcro portal
- Add generic button to remount card

## [1.0.7]
- Now when you send initial state and the Fulcro component doens't implemenet it, params are used as is instead of crashing
- Add support for `::f.portal/computed`
- Support alt+click on menu to open card in solo mode

## [1.0.6]
- Highlight text match on spotlight search
- Support ::f.portal/root-state in Fulcro portal

## [1.0.5]
- Fixes to portal persistent apps
- Fix order of actual/expected when checking `=` on tests

## [1.0.4]
- Add `stretch-flex` align

## [1.0.3]
- Add target support runner for shadow-cljs

## [1.0.2]
- Fix solo hint
- Start spotlight search  only after 2 characters
- Fulcro portal make root don't render when root props are empty

## [1.0.1]
- Use fuzzy from `com.wsscode/fuzzy`

## [1.0.0]
- Fixed react-key issues on spotlight
- Add button in the index to show/hide
- Add button to show spotlight
- Add help dialog and a action button to show it
- Improved spotlight search algorithm, details highlighed
- Styled index headers

## [1.0.0-preview9]
- Customize keyboard shortcuts (no ui, have to change on local storage manually for now)
- Add error boundaries and try catch to prevent workspaces from breaking when some exception happens during card render/refresh

## [1.0.0-preview8]
- Use new fulcro css support (requires Fulcro 2.6+)

## [1.0.0-preview7]
- Add back some required dependencies to jar

## [1.0.0-preview6]
- All tests runner
- Solo cards
- Simplified shadow-cljs setup, automatic setup hooks

## [1.0.0-preview5]
- More description at initial page

## [1.0.0-preview4]
- Open workspace via spotlight

## [1.0.0-preview3]
- Fix warning for deftest in cljs
- Prevent errors when card doesn't define refresh

## [1.0.0-preview2]
- Fix deftest to work with Clojure code

## [1.0.0-preview1]
- Initial release 🎉
