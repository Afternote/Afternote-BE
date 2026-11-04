#!/usr/bin/env python3
"""Validate generated OpenAPI against synthetic responses exported by JUnit.

Install scripts/afternote-contract-requirements.txt in a virtual environment first.
Run OpenApiGeneratedDocsTest before this script. No server or credentials needed.
"""
import copy
import json
from pathlib import Path

from openapi_schema_validator import OAS30Validator
from referencing import Registry, Resource
from referencing.jsonschema import DRAFT4


def main():
    directory = Path('build/reports/afternote-contract')
    document = json.loads((directory / 'openapi.json').read_text())
    examples = json.loads((directory / 'responses.json').read_text())
    registry = Registry().with_resource(
        'urn:afternote', Resource.from_contents(document, default_specification=DRAFT4)
    )
    validator = OAS30Validator(
        {'$ref': 'urn:afternote#/components/schemas/AfternotedetailResponse'},
        registry=registry, format_checker=OAS30Validator.FORMAT_CHECKER,
    )
    rejected = 0

    def rejects(payload):
        nonlocal rejected
        assert not validator.is_valid(payload), 'Invalid response accepted by detail schema'
        rejected += 1

    for example in examples:
        payload = example['payload']
        errors = list(validator.iter_errors(payload))
        if errors:
            raise AssertionError(f"{example['schema']}: {errors[0].message}")
        for field in ('category', 'isDraft'):
            invalid = copy.deepcopy(payload)
            invalid.pop(field)
            rejects(invalid)
        invalid = copy.deepcopy(payload)
        invalid['category'] = 'UNKNOWN'
        rejects(invalid)
        if payload['category'] in ('SOCIAL', 'BUSINESS') and not payload['isDraft']:
            for value in (None, {}, {'id': 'dummy'}, {'password': 'dummy'},
                          {'id': '', 'password': 'dummy'}, {'id': 'dummy', 'password': None}):
                invalid = copy.deepcopy(payload)
                invalid['credentials'] = value
                rejects(invalid)
            invalid = copy.deepcopy(payload)
            invalid.pop('credentials')
            rejects(invalid)
        if payload['category'] == 'PLAYLIST' and not payload['isDraft']:
            for value in (None, {}, {'songs': []}, {'songs': None}):
                invalid = copy.deepcopy(payload)
                invalid['playlist'] = value
                rejects(invalid)
            invalid = copy.deepcopy(payload)
            invalid.pop('playlist')
            rejects(invalid)
        invalid = copy.deepcopy(payload)
        if payload['category'] in ('SOCIAL', 'BUSINESS', 'GALLERY'):
            invalid['playlist'] = {'songs': []}
        else:
            invalid['credentials'] = {'id': 'dummy', 'password': 'dummy'}
        rejects(invalid)
    assert len(examples) >= 8, 'Missing category/state response examples'
    print(f'OpenAPI validation: {len(examples)} responses accepted; {rejected} invalid variants rejected')


if __name__ == '__main__':
    main()
