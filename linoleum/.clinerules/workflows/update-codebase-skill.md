Take @/docs/design.md into account

/codebase-summary.sop.md with the following parameters:

- output_dir : .cline/skills/linoleum-codebase
- consolidate_targets : SKILL.md
- consolidate_prompt : consolidate the content following the Agent Skills format described on .clinerules/agent-skills-specification.md for a skill with description "Understand the design of the 'linoleum' package, and how the codebase works. Use that information when analysing the code, making code changes, and proposing new technical designs"
- update_mode : true is output_dir already exists ; false otherwise.
