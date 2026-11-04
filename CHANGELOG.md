# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

This plugin is a fork of [Pipeline: Input Step](https://github.com/jenkinsci/pipeline-input-step-plugin),
based on upstream release
[560.v56198a_642157](https://github.com/jenkinsci/pipeline-input-step-plugin/releases/tag/560.v56198a_642157)
plus later dependency updates. For changes before the fork, see the upstream releases.

## [Unreleased]

### Fixed

- `scripts/setup-signing.sh` stores the multiline signing key and certificate
  correctly; the previous `gh secret set --body -` form saved an empty key.

## [1.0] - 2026-11-03

### Added

- `skip` option on the input step: offers a third outcome alongside proceed and
  abort, both as an inline console link and as a form button. Choosing it
  resumes the pipeline and returns `skipValue` (default `SKIPPED`). Requires the
  same permission as proceed (Job/Build).
- Release workflow that publishes the `.hpi` and a signed `update-center.json`
  to GitHub Releases on `v*` tags.

### Changed

- Renamed the plugin so it coexists with the stock input step: artifact
  `pipeline-advanced-input-step`, step `advancedInput`, package
  `io.jenkins.plugins.advancedinput`. Pipelines call `advancedInput`; `input`
  remains the stock step.

[Unreleased]: https://github.com/bootswithdefer/pipeline-advanced-input-step-plugin/compare/v1.0...HEAD
[1.0]: https://github.com/bootswithdefer/pipeline-advanced-input-step-plugin/releases/tag/v1.0
