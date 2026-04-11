<?php

namespace Tests\Feature;

use Illuminate\Foundation\Testing\RefreshDatabase;
use Tests\TestCase as BaseTestCase;

abstract class FeatureTestCase extends BaseTestCase
{
    use RefreshDatabase;
}
